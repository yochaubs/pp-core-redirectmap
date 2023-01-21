package at.website.global.config.core.redirectmaps;

import com.adobe.acs.commons.redirectmaps.models.MapEntry;
import com.adobe.acs.commons.redirectmaps.models.RedirectMapModel;
import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.wcm.api.NameConstants;
import com.google.common.base.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;


@Component(service = {Servlet.class}, property = {
  "sling.servlet.methods=POST",
  "sling.servlet.resourceTypes=acs-commons/components/utilities/redirectmappage"
})
public class RedirectEntriesUploadServlet extends SlingAllMethodsServlet {
  private static final long serialVersionUID = -4079230227805890306L;

  private static final Logger log = LoggerFactory.getLogger(RedirectEntriesUploadServlet.class);

  private static final String WHITESPACE_MSG = "Extra whitespace found in entry %s";

  private static final String NO_TARGET_MSG = "No target found in entry %s";


  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
        throws  IOException {
    log.debug("doPost");

    List<MapEntry> entries = new ArrayList<>();
    List<MapEntry> entriesFromRequest = getMapEntries(request);
    Resource redirectMap = request.getResource().getChild(RedirectMapModel.MAP_FILE_NODE);

    if (redirectMap != null) {
        entries = getEntriesFromIS(redirectMap.adaptTo(InputStream.class));
        setValidEntry(entries, entriesFromRequest);
    }

    entries.addAll(entriesFromRequest);

    updateRedirectMap(request, getValidLines(entries));
  }

  /**
   * set valid false if source already exist
   * @param entriesFromResource - entries from redirect map
   * @param entriesFromRequest - entries from uploaded file
   */
  private void setValidEntry(List<MapEntry> entriesFromResource, List<MapEntry> entriesFromRequest) {
    for (MapEntry entry : entriesFromResource) {
      for (MapEntry entryFromRequest : entriesFromRequest) {
            if (entry.getSource().equalsIgnoreCase(entryFromRequest.getSource())) {
               entry.setValid(false);
            }
      }
    }
  }

  /**
   *
   * @param entries - Mapentry
   * @return Valid entries from aggregated entries
   */
  private List<String> getValidLines(List<MapEntry> entries) {
    List<String> validLines = new ArrayList<>();
    for (MapEntry entry : entries) {
      if (entry.isValid()) {
        validLines.add(entry.getSource() + " " + entry.getTarget());
      }
    }
    return validLines;
  }

  /**
   *
   * @param request - Slingrequest
   * @return List of entries from uploaded file
   * @throws IOException throw IO exception if file is missing
   */
  private List<MapEntry> getMapEntries(SlingHttpServletRequest request) throws IOException {
    List<MapEntry> entries = new ArrayList<>();
    final Map<String, RequestParameter[]> params = request.getRequestParameterMap();
    for (final Map.Entry<String, RequestParameter[]> pairs : params.entrySet()){
      final RequestParameter[] pArr = pairs.getValue();
      final RequestParameter param = pArr[0];
      final InputStream stream = param.getInputStream();
      if(!param.isFormField() && param.getFileName() !=null  && param.getFileName().equals(RedirectMapModel.MAP_FILE_NODE)) {
        entries = getEntriesFromIS(stream);
      }
    }
    return entries;
  }

  private List<MapEntry> getEntriesFromIS(InputStream is) throws IOException {
    List<MapEntry> entries =  new ArrayList<>();
    long id = 0;
    for (String line : IOUtils.readLines(is, StandardCharsets.UTF_8)) {
      MapEntry entry = toEntry(id++, line);
      if (entry != null) {
        entries.add(entry);
      }
    }
    return entries;
  }

   static void updateRedirectMap(SlingHttpServletRequest request, List<String> entries)
    throws PersistenceException {
    Resource resource = request.getResource();

    log.info("Updating redirect map at {}", request.getResource().getPath());

    Calendar now = Calendar.getInstance();
    ModifiableValueMap contentProperties = resource.adaptTo(ModifiableValueMap.class);
    if (contentProperties == null) {
      throw new PersistenceException("Failed to retrieve resource " + resource + " for editing");
    }
    contentProperties.put(NameConstants.PN_PAGE_LAST_MOD, now);
    contentProperties.put(NameConstants.PN_PAGE_LAST_MOD_BY, request.getResourceResolver().getUserID());

    Map<String, Object> fileParams = new HashMap<>();
    fileParams.put(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FILE);
    Resource fileResource = ResourceUtil.getOrCreateResource(request.getResourceResolver(),
      resource.getPath() + "/" + RedirectMapModel.MAP_FILE_NODE, fileParams, JcrConstants.NT_UNSTRUCTURED,
      false);

    Map<String, Object> contentParams = new HashMap<>();
    contentParams.put(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_RESOURCE);
    contentParams.put(JcrConstants.JCR_MIMETYPE, "text/plain");
    Resource contentResource = ResourceUtil.getOrCreateResource(resource.getResourceResolver(),
      fileResource.getPath() + "/" + JcrConstants.JCR_CONTENT, contentParams, JcrConstants.NT_UNSTRUCTURED,
      false);

    ModifiableValueMap mvm = contentResource.adaptTo(ModifiableValueMap.class);
    if (mvm == null) {
      throw new PersistenceException("Retrieve resource " + contentResource + " for editing");
    }
    mvm.put(JcrConstants.JCR_DATA,
      new ByteArrayInputStream(StringUtils.join(entries, "\n").getBytes(Charsets.UTF_8)));
    mvm.put(JcrConstants.JCR_LASTMODIFIED, now);
    mvm.put(JcrConstants.JCR_LAST_MODIFIED_BY, request.getResourceResolver().getUserID());
    request.getResourceResolver().commit();
    request.getResourceResolver().refresh();
    log.debug("Changes saved...");
  }

  /**
   *  Convert lines from txt file to MapEntry
   * @param id - id of entry
   * @param l - line from file
   * @return MapEntry
   */
  private MapEntry toEntry(long id, String l) {
    String[] seg = l.split("\\s+");

    MapEntry entry = null;
    if (org.apache.commons.lang3.StringUtils.isBlank(l) || l.startsWith("#")) {
      // Skip as the line is empty or a comment
    } else if (seg.length == 2) {
      entry = new MapEntry(id, seg[0], seg[1], "File");
    } else if (seg.length > 2) {
      entry = new MapEntry(id, seg[0], seg[1], "File");
      entry.setValid(false);
      entry.setStatus(String.format(WHITESPACE_MSG, l));
    } else {
      entry = new MapEntry(id, seg[0], "", "File");
      entry.setValid(false);
      entry.setStatus(String.format(NO_TARGET_MSG, l));
    }
    return entry;
  }
}
