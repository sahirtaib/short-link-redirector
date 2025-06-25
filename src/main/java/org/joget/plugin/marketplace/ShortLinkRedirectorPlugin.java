package org.joget.plugin.marketplace;

import org.joget.apps.app.model.PluginWebFilterAbstract;
import org.joget.plugin.base.SystemConfigurablePlugin;
import org.joget.apps.app.service.AppUtil;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class ShortLinkRedirectorPlugin extends PluginWebFilterAbstract implements SystemConfigurablePlugin {

    private static final String CONTEXT_PATH = "/jw";

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
        return "Redirects shorten URL to target URL.";
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/"+ getName() +".json", null, false, null);
    }

    @Override
    public String[] getUrlPatterns() {
        return new String[]{getUriListener() + "/*"};
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (!isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String requestedUri = httpRequest.getRequestURI();          // example: /jw/web/users/profile
        String uriListener = CONTEXT_PATH + getUriListener() + "/"; // example: /jw/s/

        if (requestedUri.startsWith(uriListener)) {
            String id = requestedUri.substring(uriListener.length());

            if (!id.isEmpty()) {
                Map mapping = getUrlMapping(id);

                // if unable to resolve short URL, show 404 not found error
                if (mapping == null) {
                    httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND, "Short URL not found.");
                    return;
                }

                String targetUrl = ensureFullyQualified(mapping.get("targetUrl").toString(), httpRequest);
                String behavior = mapping.get("behavior").toString();
                boolean isInternalUrl = isInternalUrl(targetUrl, httpRequest);

                // external URL will always be redirected, regardless of behavior
                if ("redirect".equalsIgnoreCase(behavior) || !isInternalUrl) {
                    httpResponse.sendRedirect(targetUrl);
                    return;
                }

                // internal URL can be forwarded
                if ("forward".equalsIgnoreCase(behavior) && isInternalUrl) {
                    URI uri = getUri(targetUrl);

                    if (uri != null) {
                        targetUrl = uri.getPath().substring(CONTEXT_PATH.length()) + "?" + uri.getQuery();
                        httpRequest.getRequestDispatcher(targetUrl).forward(httpRequest, httpResponse);
                        return;
                    }
                }
            }
        }

        httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND, "Short URL not found.");
    }


    private boolean isEnabled() {
        return "true".equalsIgnoreCase(getPropertyString("enabled"));
    }

    private String getUriListener() {
        String uriListener = getPropertyString("uriListener");

        if (uriListener.isEmpty()) {
            return "/s";
        }

        // remove whitespace/slash at leading/trailing uriListener
        uriListener = uriListener.trim().replaceAll("^/+", "").replaceAll("/+$", "");

        // remove firstUri if its included
        if (uriListener.startsWith(CONTEXT_PATH.replaceFirst("^/+", ""))) {
            uriListener = uriListener.substring(CONTEXT_PATH.length()).replaceAll("^/+", "");
        }

        return "/" + uriListener; // example: /s
    }

    private Map getUrlMapping(String id) {
        Map mapping = null;
        Object[] urlMappings = (Object[]) properties.get("urlMappings");

        if (urlMappings != null) {
            for (Object urlMapping : urlMappings) {
                mapping = (HashMap) urlMapping;
                String key = mapping.get("shortKey").toString();

                if (id.equals(key)) {
                    break;
                }
            }
        }

        return mapping;
    }

    private String ensureFullyQualified(String url, HttpServletRequest request) {
        if (url == null || url.trim().isEmpty()) {
            return "";
        }

        String queryString = request.getQueryString() == null ? "" : "?" + request.getQueryString();
        url = url.trim() + queryString;

        String contextPath = request.getContextPath(); // "/jw"
        String domain = request.getServerName();       // "localhost"
        int port = request.getServerPort();
        String scheme = request.getScheme();           // "http" or "https"

        if (url.toLowerCase().startsWith("http://") || url.toLowerCase().startsWith("https://")) {
            return url;
        }

        if (url.matches("^[\\w.-]+\\.[a-zA-Z]{2,}.*")) {
            return "http://" + url;
        }

        if (!url.startsWith("/")) {
            url = "/" + url;
        }

        if (!url.startsWith(contextPath + "/") && !url.equals(contextPath)) {
            url = contextPath + url;
        }

        return scheme + "://" + domain + getPort(scheme, port) + url;
    }

    private String getPort(String scheme, int port) {
        return (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443) ? "" : ":" + port;
    }

    private boolean isInternalUrl(String targetUrl, HttpServletRequest request) {
        try {
            URI uri = new URI(targetUrl);
            String targetHost = uri.getHost();
            String requestHost = request.getServerName();

            if (targetHost == null) {
                return false; // malformed or relative URL
            }

            return targetHost.equalsIgnoreCase(requestHost);

        } catch (Exception e) {
            return false;
        }
    }

    private URI getUri(String url) {
        try {
            return new URI(url);
        } catch (Exception e) {
            return null;
        }
    }
}