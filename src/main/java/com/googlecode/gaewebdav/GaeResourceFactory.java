package com.googlecode.gaewebdav;

import com.bradmcevoy.http.*;
import com.newatlanta.appengine.vfs.provider.GaeVFS;
import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.FileSystemException;
import org.apache.commons.vfs.FileType;

/**
 * @author will
 * @date Nov 7, 2009 1:20:07 AM
 */
public class GaeResourceFactory implements ResourceFactory {
    @Override
    public Resource getResource(String host, String path) {
        try {
            System.out.println("path="+path);
            final FileObject fileObject = GaeVFS.resolveFile("gae://"+path);
            if(fileObject.exists()) {
                if(fileObject.getType() == FileType.FOLDER) {
                    return new GaeDirectoryResource(fileObject);
                } else {
                    return new GaeFileResource(fileObject);
                }
            } else {
                return null;
            }
        } catch (FileSystemException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public String getSupportedLevels() {
        return "1,2";
    }
}
