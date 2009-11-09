package com.googlecode.gaewebdav;

import com.bradmcevoy.http.DefaultResponseHandler;
import com.bradmcevoy.http.ResourceFactory;
import com.bradmcevoy.http.ResourceFactoryFactory;
import com.bradmcevoy.http.ResponseHandler;
import com.newatlanta.appengine.vfs.provider.GaeVFS;
import org.apache.commons.vfs.FileSystemException;

/**
 * @author will
 * @date Nov 7, 2009 1:54:02 AM
 */
public class GaeResourceFactoryFactory implements ResourceFactoryFactory {
    @Override
    public ResponseHandler createResponseHandler() {
        return new DefaultResponseHandler();
    }

    @Override
    public void init() {
        GaeContext.init();
        try {
            GaeVFS.resolveFile("gae:///").createFolder();
            try {
                final String path = GaeVFS.getManager().getBaseFile().getName().getPathDecoded();
                GaeContext.LOG.info("basePath="+path);
                GaeContext.putData("basePath", path.getBytes());
            } catch (FileSystemException e) {
                e.printStackTrace();
            }
        } catch (FileSystemException e) {
            e.printStackTrace();
        }
    }

    @Override
    public ResourceFactory createResourceFactory() {
        return new GaeResourceFactory();
    }
}
