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
import java.util.logging.Logger;

/**
 * @author will
 * @date Nov 7, 2009 1:54:42 AM
 */
public class GaeFileResource implements FileResource {
    private static final Logger log = Logger.getLogger( GaeFileResource.class.getName() );
    
    public final FileObject fileObject;

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
            log.info("Send range content!");
            final RandomAccessContent random = fileObject.getContent().getRandomAccessContent(RandomAccessMode.READ);
            random.seek(range.getStart());
            final InputStream input = random.getInputStream();
            ByteHelper.copy(out,input);
        } else {
            // Send the whole file
            log.info("Send content! params="+params+",type="+contentType);
            final InputStream input = fileObject.getContent().getInputStream();
            try {
                ByteHelper.copy(out,input);
                out.close();
                input.close();
            } catch (Exception e) {
                log.info("Send ended early:"+e.getMessage());
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
        log.info("moving file! dest="+rDest.getName()+",name="+name);
        try {
            if(rDest instanceof GaeDirectoryResource) {
                final FileObject destFile = ((GaeDirectoryResource)rDest).fileObject.resolveFile(name);
                if(!fileObject.canRenameTo(destFile)) {
                    throw new ConflictException(rDest.child(name));
                }
                fileObject.moveTo(destFile);
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
        log.info("process form!");
        return null;
    }

    @Override
    public Object authenticate(String user, String password) {
        log.info("authenticate!");
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
        log.info("getrealm");
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
            return (String) fileObject.getContent().getAttribute("mime-type");
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
        log.info("copying file! dest="+toCollection.getName()+",name="+name);
        try {
            if(toCollection instanceof GaeDirectoryResource) {
                final FileObject destFile = ((GaeDirectoryResource)toCollection).fileObject.resolveFile(name);
                FileUtil.copyContent(fileObject,destFile);
            } else {
                throw new IOException("Wrong resource type");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
