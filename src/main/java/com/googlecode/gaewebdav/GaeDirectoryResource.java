package com.googlecode.gaewebdav;

import com.bradmcevoy.http.*;
import com.bradmcevoy.http.exceptions.ConflictException;
import com.bradmcevoy.http.exceptions.NotAuthorizedException;
import eu.medsea.mimeutil.MimeType;
import eu.medsea.mimeutil.MimeUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.vfs.*;
import org.apache.commons.vfs.util.RandomAccessMode;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.logging.Logger;

/**
 * WebDAV Resource representation of a directory or "Collection".
 * Implemented as a layer on top of Apache VFS FileObjects.
 * @author will
 * @date Nov 7, 2009 2:38:23 AM
 */
public class GaeDirectoryResource implements FolderResource {
    private static final Logger log = Logger.getLogger( GaeDirectoryResource.class.getName() );

    public FileObject fileObject;

    public GaeDirectoryResource(FileObject fileObject) throws FileSystemException {
        this.fileObject = fileObject;
    }

    /**
     * Manually generates a standard HTML directory listing suitable for standard filesystem web browsing.
     */
    private String DirectoryListing(FileObject fileObject) throws FileSystemException {
        final String cacheHash = "#"+fileObject.hashCode();
        final Object cached = GaeContext.getCached(cacheHash);
        if(cached != null) {
            return (String) cached;
        }

        StringWriter writer = new StringWriter();
        writer.write("<html><head><title>Index of /" + getPath(fileObject) + "</title></head>");
        writer.write("<body bgcolor=\"white\">\n");
        writer.write("<h1>Index of /" + getPath(fileObject) + "</h1><hr><pre>\n");
        if (fileObject.getParent() != null) {
            writer.write("<a href=\"/"+getPath(fileObject.getParent())+"\">../</a>\n");
        }
        final FileObject[] files = fileObject.getChildren();
        for (FileObject file : files) {
            if (file.exists()) {
                final boolean isFolder = file.getType().equals(FileType.FOLDER);
                String path = file.getName().getBaseName() + (isFolder ? "/" : "");
                path = trim(path, 30);
                writer.write("<a href=\"/" + getPath(file) + "\">" + path + "</a>");
                writer.write(fill(' ',30-path.length()));

                final String date = new Date(file.getContent().getLastModifiedTime()).toString();
                writer.write(date);
                writer.write(fill(' ',30-date.length()));
                writer.write(isFolder ? "-" : (String.valueOf(file.getContent().getSize())));
                writer.write("\n");
            }
        }
        writer.write("</pre><hr>");
        writer.write("<em>"+"Powered by <a href=\"http://gae-webdav.appspot.com\">Google App Engine WebDAV</a></em>"+new Date().toString());
        writer.write(" <a href=\"/admin\">admin</a>\n");
        writer.write("</body></html>");
        final String s = writer.toString();
        GaeContext.putCached(cacheHash,s,1000*60*5);
        return s;
    }

    private String fill(char c, int length) {
        StringBuffer s = new StringBuffer(length);
        for(int i = 0; i<length;i++) s.append(c);
        return s.toString();
    }

    private String trim(String s, int length) {
        if(s.length() > length) {
            s = s.substring(0,length-3) + "...";
        }
        return s;
    }

    private String getPath() {
        return getPath(fileObject);
    }

    /**
     * Returns a relative path segment NOT beginning with "/".
     */
    private String getPath(FileObject file) {
        try {
            String s = file.getName().getPathDecoded();
            if(s.startsWith("/")) s = s.substring(1);
            return s;
        } catch (FileSystemException e) {
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public String getName() {
        try {
            if (fileObject.getParent() == null) {
                return "/";
            }
        } catch (FileSystemException e) {
            e.printStackTrace();
            return "";
        }
        return fileObject.getName().getBaseName();
    }

    @Override
    public String getContentType(String accepts) {
        if(accepts != null && accepts.contains("text/html")) return "text/html";
        return MimeUtil.UNKNOWN_MIME_TYPE.toString();
    }

    @Override
    public void sendContent(OutputStream out, Range range, Map<String, String> params, String contentType) throws IOException, NotAuthorizedException {
        // Manually construct a simple directory listing
        log.info("Sending directory content: " + contentType+",range="+range);
        if(contentType == null || !contentType.equals("text/html")) {
            out.close();
            return;
        }
        log.info("Sending directory listing");

        byte[] directoryListing = DirectoryListing(fileObject).getBytes();
        out.write(directoryListing);
        out.close();
        // Allow listing to be refreshed next time
        GaeContext.expireCached("#"+fileObject.hashCode());
    }

    /**
     * @return null if the specified child does not exist.
     */
    @Override
    public Resource child(String childName) {
        try {
            final FileObject child = fileObject.getChild(childName);
            return (child == null || !child.exists()) ? null : new GaeFileResource(child);
        } catch (FileSystemException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public List<? extends Resource> getChildren() {
        List<Resource> children = new ArrayList<Resource>();
        try {
            for (FileObject child : fileObject.getChildren()) {
                if (child.exists()) {
                    children.add((child.getType() == FileType.FOLDER) ?
                            new GaeDirectoryResource(child) :
                            new GaeFileResource(child));
                }
            }
        } catch (FileSystemException e) {
            e.printStackTrace();
        }
        return children;
    }

    @Override
    public Resource createNew(String newName, InputStream inputStream, Long length, String contentType) throws IOException {
        try {
            if (length == null) {
                length = 0L;
            }
            log.info("Create new file in "+getPath()+"! name=" + newName+", contentType="+contentType);
            final FileObject newFile = fileObject.resolveFile(newName);
            if(contentType != null && contentType.equals(MimeUtil.DIRECTORY_MIME_TYPE.toString())) {
                newFile.createFolder();
                return new GaeDirectoryResource(newFile);
            }
            newFile.createFile();
            final OutputStream out = newFile.getContent().getOutputStream();
            final int i = ByteHelper.copy(out, inputStream);
            log.info("Transferred " + i + " bytes!");
            if (i != length) {
                throw new IOException("Specified length does not match file upload");
            }
            out.close();
            inputStream.close();
            setMimeType(contentType == null ? MimeUtil.UNKNOWN_MIME_TYPE.toString() : contentType, newFile);
            return new GaeFileResource(newFile);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void setMimeType(String mimeType, FileObject fileObject) throws FileSystemException {
        final String finalType;
        final FileContent content = fileObject.getContent();
        if (!MimeUtil.UNKNOWN_MIME_TYPE.toString().equals(mimeType)) {
            log.info("Client passed mime type: " + mimeType);
            final String[] types = mimeType.split(",");
            Collection<MimeType> typeList = new ArrayList<MimeType>();
            for (String type : types) {
                typeList.add(new MimeType(type));
            }
            finalType = MimeUtil.getMostSpecificMimeType(typeList).toString();
        } else {
            final Collection<MimeType> types = MimeUtil.getMimeTypes(getName());
            log.info("name types=" + types);
            try {
                InputStream in = content.getRandomAccessContent(RandomAccessMode.READ).getInputStream();
                final Collection<MimeType> types2 = MimeUtil.getMimeTypes(in);
                log.info("stream types=" + types2);
                types.addAll(types2);
                in.close();
            } catch (Exception e) {
                log.info("Error loading stream!");
                e.printStackTrace();
            }
            if (types.size() > 1) {
                types.remove(MimeUtil.UNKNOWN_MIME_TYPE);
            }
            finalType = MimeUtil.getMostSpecificMimeType(types).toString();
            log.info("Deriving mime type from file, final type=" + finalType);
        }
        log.info("Setting MIME type to " + finalType);
        content.setAttribute("mime-type", finalType);
    }

    @Override
    public CollectionResource createCollection(String newName) throws NotAuthorizedException, ConflictException {
        try {
            log.info("Creating new folder in "+getPath()+", name="+newName);
            final FileObject newFolder = fileObject.resolveFile(newName);
            if(newFolder.exists()) {
                return null;
            }
            newFolder.createFolder();
            return new GaeDirectoryResource(newFolder);
        } catch (FileSystemException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Long getContentLength() {
        final String s = GaeContext.request().getHeader(Request.Header.ACCEPT_ENCODING.name());
        if(s != null && s.contains("text/html")) {
            try {
                return (long) DirectoryListing(fileObject).getBytes().length;
            } catch (Exception e) {
                log.warning("Error: "+e.getMessage());
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public Long getMaxAgeSeconds(Auth auth) {
        return null;
    }

    @Override
    public String getUniqueId() {
        return fileObject.hashCode() + "";
    }

    @Override
    public Object authenticate(String user, String password) {
        // Generate user/password hash
        final String hash = DigestUtils.md5Hex("name:" + user + password);

        // Check database for hash exists
        final byte[] token = GaeContext.getData(hash);
        if(token.length > 0) {
            // entry exists, let's use it
            // TODO: Expire hash authentication after a period of time
            log.info("Logged in previous used "+user);
            return new String(token);
        }

        try {
            URL url = new URL("https://www.google.com/accounts/ClientLogin");
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            StringBuffer b = new StringBuffer();
            set(b,"accountType","HOSTED_OR_GOOGLE");
            set(b,"Email",user+(user.contains("@")?"@gmail.com":""));
            set(b,"Passwd",password);
            set(b,"service","cp");
            set(b,"source","gae-webdav");
            b.deleteCharAt(b.length()-1); // Remove trailing &
            DataOutputStream printout = new DataOutputStream(connection.getOutputStream());
            printout.writeBytes(b.toString());
            printout.flush();
            printout.close();
            DataInputStream input = new DataInputStream(connection.getInputStream());
            int i = connection.getResponseCode();
            if(i == 200) {
                final Properties responseProperties = new Properties();
                responseProperties.load(new InputStreamReader(input));
                final String auth = responseProperties.getProperty("Auth");
                log.info("Successfully logged in "+user+", auth token="+auth+"!");
                GaeContext.putData(hash,auth.getBytes());
                return auth;
            } else if(i == 403) {
                log.info("Login failed!");
                log.info("Response: "+new String(ByteHelper.loadBytes(input)));
                return null;
            } else {
                log.info("Unknown error, code="+i);
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void set(StringBuffer p, String name, String value) throws UnsupportedEncodingException {
        p.append(name+"="+URLEncoder.encode(value,"UTF-8")+"&");
    }

    @Override
    public boolean authorise(Request request, Request.Method method, Auth auth) {
        return auth != null;
    }

    @Override
    public String getRealm() {
        log.info("getrealm");
        return "gae-webdav.appspot.com";
    }

    @Override
    public Date getModifiedDate() {
        try {
            if(!fileObject.exists()) {
                return null;
            }
            return new Date(fileObject.getContent().getLastModifiedTime());
        } catch (FileSystemException e) {
            return null;
        }
    }

    @Override
    public String checkRedirect(Request request) {
        return null;
    }

    @Override
    public void moveTo(CollectionResource rDest, String name) throws ConflictException {
        log.info("moving folder! dest="+rDest.getName()+",name="+name);
        try {
            if(rDest instanceof GaeDirectoryResource) {
                final FileObject destFile = ((GaeDirectoryResource)rDest).fileObject.resolveFile(name);
                if(!fileObject.canRenameTo(destFile)) {
                    throw new ConflictException(rDest.child(name));
                }
                destFile.createFolder();
                destFile.copyFrom(fileObject,new AllFileSelector());
                fileObject.delete(new AllFileSelector());
            } else {
                throw new IOException("Wrong resource type");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void delete() {
        try {
            final boolean deleted = fileObject.delete();
            if(!deleted) throw new IOException("File "+getName()+" Not deleted");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Date getCreateDate() {
        try {
            if(!fileObject.exists()) {
                return null;
            }
            return new Date(fileObject.getContent().getLastModifiedTime());
        } catch (FileSystemException e) {
            return null;
        }
    }

    @Override
    public void copyTo(CollectionResource toCollection, String name) {
        log.info("Copying file! dest="+toCollection.getName()+",name="+name);
        try {
            if(toCollection instanceof GaeDirectoryResource) {
                final FileObject destFile = ((GaeDirectoryResource)toCollection).fileObject.resolveFile(name);
                if(destFile.exists()) {
                    destFile.delete(new AllFileSelector());
                }
                destFile.createFolder();
                destFile.copyFrom(fileObject,new AllFileSelector());
            } else {
                throw new IOException("Wrong resource type");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
