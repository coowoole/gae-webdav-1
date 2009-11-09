package com.googlecode.gaewebdav;

import com.bradmcevoy.http.Resource;
import com.bradmcevoy.http.ResourceFactory;
import com.newatlanta.appengine.vfs.provider.GaeVFS;
import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.FileSystemException;
import org.apache.commons.vfs.FileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author will
 * @date Nov 7, 2009 1:20:07 AM
 */
public class GaeResourceFactory implements ResourceFactory {
    private static final Logger log = LoggerFactory.getLogger(GaeResourceFactory.class);

    @Override
    public Resource getResource(String host, String path) {
        Resource resource;
        try {
            final FileObject fileObject = GaeVFS.resolveFile("gae://"+path);
            if(fileObject.exists()) {
                if(fileObject.getType() == FileType.FOLDER) {
                    resource = new GaeDirectoryResource(fileObject);
                    return resource;
                } else {
                    resource = new GaeFileResource(fileObject);
                    return resource;
                }
            } else {
                return null;
            }
        } catch (FileSystemException e) {
            e.printStackTrace();
            return null;
        } finally {
        }
    }

    @Override
    public String getSupportedLevels() {
        return "1,2";
    }
}
