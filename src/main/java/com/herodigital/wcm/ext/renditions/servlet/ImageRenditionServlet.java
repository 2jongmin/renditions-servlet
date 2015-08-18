package com.herodigital.wcm.ext.renditions.servlet;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Rendition;
import com.herodigital.wcm.ext.renditions.model.RenditionMeta;
import com.herodigital.wcm.ext.renditions.service.AssetRenditionResolver;

/**
 * Handles fetching of an asset image rendition. Primary advantage over out of the box AEM
 * thumbnail servlet is the support of both web and thumbnail renditions.
 * <p>
 * Resolution of rendition is handled by {@link AssetRenditionResolver}.
 * <p>
 * 404 response is returned if requested asset or rendition does not exist.
 * 
 * @author joelepps
 *
 */
@SlingServlet(
		resourceTypes = "sling/servlet/default",
		selectors = { ImageRenditionServlet.SELECTOR_RENDITION_WEB, ImageRenditionServlet.SELECTOR_RENDITION_THUMB, ImageRenditionServlet.SELECTOR_RENDITION_ORIGINAL }, 
		extensions = { "png", "jpg", "jpeg" }, 
		methods = { "GET" }, 
		label = "Web Image Rendition Servlet", 
		description = "Servlet which returns the web image rendition")
@Properties({
		@Property(name = "service.description", value = "Servlet which returns the web image rendtions"),
		@Property(name = "service.vendor", value = "Hero Digital") 
})
public class ImageRenditionServlet extends SlingSafeMethodsServlet {

private static final long serialVersionUID = 7272927969196706776L;
	
	private static final Logger log = LoggerFactory.getLogger(ImageRenditionServlet.class);
	
	protected static final String SELECTOR_RENDITION_WEB = "imgw";
	protected static final String SELECTOR_RENDITION_THUMB = "imgt";
	protected static final String SELECTOR_RENDITION_ORIGINAL ="imgo";
	
	private static enum Selector { TYPE, WIDTH, HEIGHT }
	
	@Reference
	private AssetRenditionResolver assetRenditionResolver;
	
	@Override
	protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
		RenditionMeta renditionMeta = buildRenditionMeta(request);
		if (renditionMeta == null) {
			log.debug("Failed to build rendition meta for {}", request.getPathInfo());
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		Resource resource = request.getResource();
		if (resource == null) {
			log.debug("Missing dam asset at {}", request.getPathInfo());
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		Asset damAsset = resource.adaptTo(Asset.class);
		if (damAsset == null) {
			log.debug("Cannot resolve dam asset at {} for {}", resource.getPath(), request.getPathInfo());
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		Rendition rendition = assetRenditionResolver.resolveRendition(damAsset, renditionMeta);
		if (rendition == null) {
			log.debug("Missing rendition for {} and {}", renditionMeta, damAsset.getPath());
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		InputStream input = rendition.getStream();
		if (input == null) {
			log.error("Missing rendition input stream for {} and {}", renditionMeta, damAsset.getPath());
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}
		
		writeResponse(response, input, rendition.getMimeType());
	}
	
	private static RenditionMeta buildRenditionMeta(SlingHttpServletRequest request) {
		String[] selectors = request.getRequestPathInfo().getSelectors();
		String extension = request.getRequestPathInfo().getExtension();
		
		RenditionSelector renditionSelector = null;
		int width = 0;
		int height = 0;
		if (selectors.length >= 1 && SELECTOR_RENDITION_ORIGINAL.equals(selectors[0])) {
			// original rendition does not need or parse width/height
			renditionSelector = RenditionSelector.fromSelector(selectors[Selector.TYPE.ordinal()]);
		} else if (selectors.length == 3) {
			renditionSelector = RenditionSelector.fromSelector(selectors[Selector.TYPE.ordinal()]);
			width = NumberUtils.toInt(selectors[Selector.WIDTH.ordinal()]);
			height = NumberUtils.toInt(selectors[Selector.HEIGHT.ordinal()]);
		} else {
			log.debug("Selectors size is not 1 or 3: {}", Arrays.toString(selectors));
			return null;
		}
		
		if (extension.equals("jpg")) {
			log.trace("Updating extension jpg to jpeg");
			extension = "jpeg";
		}
		
		if (renditionSelector == null) {
			log.debug("Rendition type was not recognized: {}", Arrays.toString(selectors));
			return null;
		}
		
		RenditionMeta meta = new RenditionMeta(renditionSelector.getRenditionType(), width, height, extension);
		log.trace("Build rendition meta: {}", meta);
		
		return meta;
	}
	
	private static void writeResponse(SlingHttpServletResponse response, InputStream input, String mimeType) throws IOException {
		String formatName = mimeType.contains("png") ? "PNG" : "JPG";
		response.setContentType(mimeType);
		BufferedImage bi = ImageIO.read(input);
		OutputStream out = response.getOutputStream();
		ImageIO.write(bi, formatName, out);
		out.close();
	}

}
