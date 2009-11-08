package com.googlecode.gaewebdav;

import com.bradmcevoy.http.*;
import com.bradmcevoy.http.exceptions.ConflictException;
import com.bradmcevoy.http.exceptions.NotAuthorizedException;
import com.newatlanta.commons.vfs.provider.gae.GaeVFS;
import eu.medsea.mimeutil.MimeType;
import eu.medsea.mimeutil.MimeUtil;
import org.apache.commons.vfs.*;
import org.apache.commons.vfs.util.RandomAccessMode;

import java.io.*;
import java.util.*;

/**
 * @author will
 * @date Nov 7, 2009 2:38:23 AM
 */
public class GaeDirectoryResource extends GaeFileResource implements FolderResource {

    private final byte[] directoryListing;
    final FileObject baseFile;

    public GaeDirectoryResource(FileObject fileObject) throws FileSystemException {
        super(fileObject);
        baseFile = GaeVFS.getManager().getBaseFile();
        directoryListing = DirectoryListing(fileObject).getBytes();
    }

    /**
     * Manually generates a standard HTML directory listing suitable for standard filesystem web browsing.
     */
    private String DirectoryListing(FileObject fileObject) throws FileSystemException {
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
        writer.write("</pre><hr></body></html>");
        return writer.toString();
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

    /**
     * Returns a relative path segment NOT beginning with "/".
     */
    private String getPath(FileObject file) {
        try {
            String s = file.getName().getPathDecoded().substring(baseFile.getName().getPathDecoded().length());
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
        return super.getName();
    }

    @Override
    public String getContentType(String accepts) {
        return MimeUtil.getPreferedMimeType(accepts, "text/html," + MimeUtil.DIRECTORY_MIME_TYPE).toString();
    }

    @Override
    public void sendContent(OutputStream out, Range range, Map<String, String> params, String contentType) throws IOException, NotAuthorizedException {
        // Manually construct a simple directory listing
        System.out.println("Sending directory content!!" + contentType);
        out.write(directoryListing);
        out.close();
    }

    @Override
    public Resource child(String childName) {
        try {
            final FileObject child = fileObject.getChild(childName);
            return (child.exists()) ? new GaeFileResource(child) : null;
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
            System.out.println("Create new file! name=" + newName);
            final FileObject newFile = fileObject.resolveFile(newName);
            newFile.createFile();
            final OutputStream out = newFile.getContent().getOutputStream();
            final int i = ByteHelper.copy(out, inputStream);
            System.out.println("Transferred " + i + " bytes!");
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
            System.out.println("Client passed mime type: " + mimeType);
            final String[] types = mimeType.split(",");
            Collection<MimeType> typeList = new ArrayList<MimeType>();
            for (String type : types) {
                typeList.add(new MimeType(type));
            }
            finalType = MimeUtil.getMostSpecificMimeType(typeList).toString();
        } else {
            final Collection<MimeType> types = MimeUtil.getMimeTypes(getName());
            System.out.println("name types=" + types);
            try {
                InputStream in = content.getRandomAccessContent(RandomAccessMode.READ).getInputStream();
                final Collection<MimeType> types2 = MimeUtil.getMimeTypes(in);
                System.out.println("stream types=" + types2);
                types.addAll(types2);
                in.close();
            } catch (Exception e) {
                System.out.println("Error loading stream!");
                e.printStackTrace();
            }
            if (types.size() > 1) {
                types.remove(MimeUtil.UNKNOWN_MIME_TYPE);
            }
            finalType = MimeUtil.getMostSpecificMimeType(types).toString();
            System.out.println("Deriving mime type from file, final type=" + finalType);
        }
        System.out.println("Setting MIME type to " + finalType);
        content.setAttribute("mime-type", finalType);
    }

    @Override
    public CollectionResource createCollection(String newName) throws NotAuthorizedException, ConflictException {
        try {
            final FileObject newFolder = fileObject.resolveFile(newName);
            newFolder.createFolder();
            return new GaeDirectoryResource(newFolder);
        } catch (FileSystemException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Long getContentLength() {
        return (long) directoryListing.length;
    }

    @Override
    public Long getMaxAgeSeconds(Auth auth) {
        return 1L;
    }
}
