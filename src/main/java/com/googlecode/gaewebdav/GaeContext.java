package com.googlecode.gaewebdav;

import com.bradmcevoy.http.MiltonServlet;
import com.google.appengine.api.memcache.Expiration;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.newatlanta.appengine.vfs.provider.GaeVFS;
import org.apache.commons.vfs.FileObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;

/**
 * This singleton class contains session-scoped objects valid throughout a single HTTP request.
 * Wraps around the ServletContext for convenient access throughout the application.
 *
 * @author will
 * @date Nov 8, 2009 9:41:40 AM
 */
public class GaeContext {
    public static final Logger LOG = LoggerFactory.getLogger(GaeContext.class);

    private GaeContext() {
    }

    private static ServletContext context;
    private static MemcacheService memcache;
    private static Cache cache;
    public static FileObject TEMP_FOLDER;

    public static void init() {
        try {
            cache = CacheManager.getInstance().getCacheFactory().createCache(Collections.emptyMap());
             memcache = MemcacheServiceFactory.getMemcacheService();
            TEMP_FOLDER = GaeVFS.resolveFile("/_tmp/");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static HttpServletRequest request() {
        return MiltonServlet.request();
    }
    
    public static ServletContext get() {
        return context;
    }

    public static void setContext(ServletContext context) {
            GaeContext.context = context;
    }

    public static Object get(String name) {
        return context.getAttribute(name);
    }

    public static void put(String name, Object object) {
        context.setAttribute(name, object);
    }

    public static Object getCached(String name) {
        return cache.get(name);
    }

    public static void putCached(String name, Object object) {
        cache.put(name, object);
    }

    public static void putCached(String name, Object object, int durationMs) {
        memcache.put(name, object, Expiration.byDeltaMillis(durationMs));
    }

    public static void expireCached(String name) {
        memcache.delete(name);
    }

    // Provides quick convenience methods for reading/writing data persisted across the distributed hash table,
    // implemented on top of the GaeVFS abstraction.

    public static byte[] getData(String name) {
        try {
            return ByteHelper.loadBytes(TEMP_FOLDER.resolveFile(name).getContent().getInputStream());
        } catch (Exception e) {
            LOG.info("getData failed");
            return new byte[0];
        }
    }

    public static void putData(String name, byte[] bytes) {
        try {
            final FileObject file = TEMP_FOLDER.resolveFile(name);
            file.createFile();
            file.getContent().getOutputStream().write(bytes);
        } catch (IOException e) {
            LOG.error("putData failed", e);
        }
    }
}
