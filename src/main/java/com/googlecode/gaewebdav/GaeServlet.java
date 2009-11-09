package com.googlecode.gaewebdav;

import com.bradmcevoy.http.MiltonServlet;

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
    public void service(ServletRequest servletRequest, ServletResponse servletResponse) throws ServletException, IOException {
        GaeContext.setContext(getServletConfig().getServletContext());
        super.service(servletRequest, servletResponse);
    }
}
