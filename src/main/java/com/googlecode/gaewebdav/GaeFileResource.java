package com.googlecode.gaewebdav;

import com.bradmcevoy.http.*;
import com.bradmcevoy.http.exceptions.ConflictException;
import com.bradmcevoy.http.exceptions.NotAuthorizedException;
import com.newatlanta.appengine.vfs.provider.GaeVFS;
import org.apache.commons.vfs.*;
import org.apache.commons.vfs.util.RandomAccessMode;

import java.io.*;
import java.util.Date;
import java.util.Map;

/**
 * @author will
 * @date Nov 7, 2009 1:54:42 AM
 */
public class GaeFileResource implements FileResource {
    protected final FileObject fileObject;

    public GaeFileResource(FileObject fileObject) {
        this.fileObject = fileObject;
    }

    @Override
    public String getUniqueId() {
        return fileObject.hashCode() + "";
    }

    @Override
    public void sendContent(OutputStream out, Range range, Map<String, String> params, String contentType) throws IOException, NotAuthorizedException {
        if(range != null) {
            System.out.println("Send range content!");
            final RandomAccessContent random = fileObject.getContent().getRandomAccessContent(RandomAccessMode.READ);
            random.seek(range.getStart());
            final InputStream input = random.getInputStream();
            ByteHelper.copy(out,input);
        } else {
            // Send the whole file
            System.out.println("Send content! params="+params+",type="+contentType);
            final InputStream input = fileObject.getContent().getInputStream();
            try {
                ByteHelper.copy(out,input);
                out.close();
                input.close();
            } catch (Exception e) {
                System.out.println("Send ended early:"+e.getMessage());
                try {
                    input.close();
                } catch (Exception e2) {
                    System.err.println("ERROR closing inputstream!:"+e2.getMessage());
                }
            }
        }
    }

    @Override
    public void moveTo(CollectionResource rDest, String name) throws ConflictException {
        System.out.println("moving file! dest="+rDest.getName()+",name="+name);
        try {
            final FileObject destFolder = GaeVFS.resolveFile(rDest.getName());
            final FileObject destFile = destFolder.resolveFile(name);
            fileObject.moveTo(destFile);
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
    public String getName() {
        return fileObject.getName().getBaseName();
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
    public String processForm(Map<String, String> parameters, Map<String, FileItem> files) {
        System.out.println("process form!");
        return null;
    }

    @Override
    public Object authenticate(String user, String password) {
        System.out.println("authenticate!");
        return "ok";
    }

    @Override
    public boolean authorise(Request request, Request.Method method, Auth auth) {
        return true;
    }

    @Override
    public Long getMaxAgeSeconds(Auth auth) {
        return 315360000l;
    }

    @Override
    public String getRealm() {
        System.out.println("getrealm");
        return "Google App Engine";
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
    public String getContentType(String accepts) {
        try {
            if(!fileObject.exists()) {
                return null;
            }
            final String mimeType = (String) fileObject.getContent().getAttribute("mime-type");
            System.out.println("Mime type of "+getName()+" = "+mimeType);
            return mimeType;
        } catch (Exception e) {
            e.printStackTrace();
            return "application/octet-stream";
        }
    }

    @Override
    public String checkRedirect(Request request) {
        return null;
    }

    @Override
    public Long getContentLength() {
        try {
            if(!fileObject.exists()) return null;
            return fileObject.getContent().getSize();
        } catch (FileSystemException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void copyTo(CollectionResource toCollection, String name) {
        try {
            final FileObject destFolder = GaeVFS.resolveFile(toCollection.getName());
            final FileObject destFile = destFolder.resolveFile(name);
            FileUtil.copyContent(fileObject,destFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
