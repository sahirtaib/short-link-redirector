package org.joget.plugin.marketplace;

import org.joget.apps.app.model.PluginWebFilterAbstract;
import org.joget.plugin.base.SystemConfigurablePlugin;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

public class ShortLinkRedirectorPlugin extends PluginWebFilterAbstract implements SystemConfigurablePlugin {

    private String contextPath;

    @Override
    public String getName() {
        return "ShortLinkRedirectorPlugin";
    }

    @Override
    public String getVersion() {
        return "8.2.0";
    }

    @Override
    public String getLabel() {
        return "Short Link Redirector Plugin";
    }

    @Override
    public String getDescription() {
        return "Redirects shorten URL to their full URL defined in a user form.";
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/"+ getName() +".json", null, false, null);
    }

    @Override
    public String[] getUrlPatterns() {
        return new String[]{"/" + getUriListener() + "/*"};
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String requestedUri = httpRequest.getRequestURI();  // example: /jw/web/users/profile
        this.contextPath = httpRequest.getContextPath();  // example: /jw
        String uriListener = getUriListener();              // example: s

        
        
        String baseUriListener = this.contextPath + "/" + uriListener + "/"; // example: /jw/s/

        LogUtil.info(getClassName() +"-requestedUri", requestedUri);
        LogUtil.info(getClassName() +"-baseUriListener", baseUriListener);

        if (!isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (requestedUri.startsWith(baseUriListener)) {
            String shortenUri = requestedUri.substring(baseUriListener.length());

            if (!shortenUri.isEmpty()) {
                String fullUrl = getFullUrlFromDb(shortenUri);

                if (fullUrl != null && !fullUrl.isEmpty()) {
                    httpResponse.sendRedirect(fullUrl);
                    return;
                } else {
                    httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND, "Short URL not found.");
                    return;
                }
            }
        }

        // if unable to resolve short URL, continue to next the filter chain
        filterChain.doFilter(request, response);
    }

    private String makeUserviewUrl(String id) {}

    private String getFullUrlFromDb(String shortId) {
        try {
            String formId = getPropertyString("formId");                // example: "url_mappings"
            String shortUrlField = getPropertyString("shortUrlField");  // example: "shortUrl"
            String fullUrlField = getPropertyString("fullUrlField");    // example: "fullUrl"

            FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");

            String condition = "WHERE e." + shortUrlField + " = ?";
            Object[] params = new Object[]{shortId};

            FormRowSet rowSet = dao.find(formId, null, condition, params, null, false, 0, 1);

            if (rowSet != null && !rowSet.isEmpty()) {
                FormRow row = rowSet.iterator().next();
                return row.getProperty(fullUrlField); // or row.get("fullUrl")
            }

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error using FormDataDao.find() to look up short URL.");
        }

        return null;
    }

    private String getUriListener() {
        String uriListener = getPropertyString("uriListener");

        uriListener = uriListener.trim();

        // remove contextPath /jw if its included in the uriListener
        if (uriListener.startsWith(this.contextPath)) {
            uriListener = uriListener.substring(contextPath.length());
        }

        // default to "s" if not set
        if (uriListener == null || uriListener.isEmpty()) {
            uriListener = "s";
        }

        // normalize uriListener to ensure it doesn't break the URL
        uriListener = uriListener == null ? "" : uriListener.trim();

        // remove leading/trailing slashes
        uriListener = uriListener.replaceAll("^/+", "").replaceAll("/+$", "");

        // Combine to form /jw/s/
        // String baseUriListener = normalizePath(contextPath) + uriListener + "/";

        return uriListener;
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }

        return path.startsWith("/") ? path : "/" + path;
    }

    private boolean isEnabled() {
        return "true".equalsIgnoreCase(getPropertyString("enabled"));
    }
}