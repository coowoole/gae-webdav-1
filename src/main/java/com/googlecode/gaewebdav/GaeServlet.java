package com.googlecode.gaewebdav;

import com.bradmcevoy.http.MiltonServlet;
import com.newatlanta.commons.vfs.provider.gae.GaeVFS;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

/**
 * @author will
 * @date Nov 7, 2009 2:06:49 AM
 */
public class GaeServlet extends MiltonServlet {
    @Override
    public void init(ServletConfig config) throws ServletException {
        try {
            GaeVFS.setRootPath(config.getServletContext().getRealPath("/"));
            super.init(config);
        } catch (Exception e) {
            throw new ServletException(e);
        } finally {
            GaeVFS.clearFilesCache();
        }
    }

    @Override
    public void service(ServletRequest servletRequest, ServletResponse servletResponse) throws ServletException, IOException {
        try {
            super.service(servletRequest, servletResponse);
        } catch (IOException e) {
            throw new ServletException(e);
        } finally {
            GaeVFS.clearFilesCache();
        }
    }
}
