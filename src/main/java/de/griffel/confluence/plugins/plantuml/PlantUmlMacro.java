/*
 * Copyright (C) 2011 Michael Griffel
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * This distribution includes other third-party libraries.
 * These libraries and their corresponding licenses (where different
 * from the GNU General Public License) are enumerated below.
 *
 * PlantUML is a Open-Source tool in Java to draw UML Diagram.
 * The software is developed by Arnaud Roques at
 * http://plantuml.sourceforge.org.
 */
package de.griffel.confluence.plugins.plantuml;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import net.sourceforge.plantuml.BlockUml;
import net.sourceforge.plantuml.DiagramType;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.PSystem;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.UmlSource;
import net.sourceforge.plantuml.preproc.Defines;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;

import com.atlassian.confluence.importexport.resource.DownloadResourceWriter;
import com.atlassian.confluence.importexport.resource.WritableDownloadResourceManager;
import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.renderer.PageContext;
import com.atlassian.confluence.renderer.ShortcutLinkConfig;
import com.atlassian.confluence.renderer.ShortcutLinksManager;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.plugin.PluginAccessor;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.renderer.v2.macro.BaseMacro;
import com.atlassian.renderer.v2.macro.MacroException;

import de.griffel.confluence.plugins.plantuml.preprocess.PlantUmlPreprocessor;
import de.griffel.confluence.plugins.plantuml.preprocess.PreprocessingContext;
import de.griffel.confluence.plugins.plantuml.preprocess.PreprocessingException;
import de.griffel.confluence.plugins.plantuml.preprocess.UmlSourceLocator;
import de.griffel.confluence.plugins.plantuml.type.ConfluenceLink;
import de.griffel.confluence.plugins.plantuml.type.ImageMap;
import de.griffel.confluence.plugins.plantuml.type.UmlSourceBuilder;

/**
 * The Confluence PlantUML Macro.
 * 
 * @author Michael Griffel
 */
public class PlantUmlMacro extends BaseMacro {
   private final Logger logger = Logger.getLogger(PlantUmlMacro.class);

   private final WritableDownloadResourceManager _writeableDownloadResourceManager;

   private final PageManager _pageManager;

   private final SpaceManager _spaceManager;

   private final SettingsManager _settingsManager;

   private final PluginAccessor _pluginAccessor;

   private final ShortcutLinksManager _shortcutLinksManager;

   public PlantUmlMacro(WritableDownloadResourceManager writeableDownloadResourceManager,
         PageManager pageManager, SpaceManager spaceManager, SettingsManager settingsManager,
         PluginAccessor pluginAccessor, ShortcutLinksManager shortcutLinksManager) {
      _writeableDownloadResourceManager = writeableDownloadResourceManager;
      _pageManager = pageManager;
      _spaceManager = spaceManager;
      _settingsManager = settingsManager;
      _pluginAccessor = pluginAccessor;
      _shortcutLinksManager = shortcutLinksManager;
   }

   @Override
   public boolean isInline() {
      return false;
   }

   public boolean hasBody() {
      return true;
   }

   public RenderMode getBodyRenderMode() {
      return RenderMode.NO_RENDER;
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   public String execute(Map params, final String body, final RenderContext renderContext)
         throws MacroException {

      try {
         final String unescapeHtml = unescapeHtml(body);
         return executeInternal(params, unescapeHtml, renderContext);
      } catch (final IOException e) {
         throw new MacroException(e);
      }
   }

   static String unescapeHtml(final String body) throws IOException {
      final StringWriter sw = new StringWriter();
      StringEscapeUtils.unescapeHtml(sw, body);
      return sw.toString();
   }

   protected String executeInternal(Map<String, String> params, final String body,
         final RenderContext renderContext)
         throws MacroException, IOException {
      final DownloadResourceWriter resourceWriter = _writeableDownloadResourceManager.getResourceWriter(
            AuthenticatedUserThreadLocal.getUsername(), "plantuml", ".png");

      final PlantUmlMacroParams macroParams = new PlantUmlMacroParams(params);

      if (!(renderContext instanceof PageContext)) {
         throw new MacroException("This macro can only be used in Confluence pages. (ctx="
               + renderContext.getClass().getName() + ")");
      }

      final PageContext pageContext = (PageContext) renderContext;
      final UmlSourceLocator umlSourceLocator = new UmlSourceLocatorConfluence(pageContext);
      final PreprocessingContext preprocessingContext = new MyPreprocessingContext(pageContext);

      final DiagramType diagramType = macroParams.getDiagramType();
      final UmlSourceBuilder builder = new UmlSourceBuilder(diagramType).append(new StringReader(body));
      final PlantUmlPreprocessor preprocessor =
            new PlantUmlPreprocessor(builder.build(), umlSourceLocator, preprocessingContext);
      final String umlBlock = preprocessor.toUmlBlock();

      final List<String> config = new PlantUmlConfigBuilder().build(macroParams);
      final MySourceStringReader reader = new MySourceStringReader(new Defines(), umlBlock, config);
      final ImageMap cmap = reader.renderImage(resourceWriter.getStreamForWriting());

      final StringBuilder sb = new StringBuilder();
      if (preprocessor.hasExceptions()) {
         sb.append("<div class=\"error\">");
         for (PreprocessingException exception : preprocessor.getExceptions()) {
            sb.append("<span class=\"error\">");
            sb.append("plantuml: ");
            sb.append(exception.getDetails());
            sb.append("</span><br/>");
         }
         sb.append("</div>");
      }
      if (cmap.isValid()) {
         sb.append(cmap.toHtmlString());
      }

      if (umlBlock.matches(PlantUmlPluginInfo.PLANTUML_VERSION_INFO_REGEX)) {
         sb.append(new PlantUmlPluginInfo(_pluginAccessor).toHtmlString());
      }

      sb.append("<div class=\"image-wrap\" style=\"" + macroParams.getAlignment().getCssStyle() + "\">");
      sb.append("<img");
      if (cmap.isValid()) {
         sb.append(" usemap=\"#");
         sb.append(cmap.getId());
         sb.append("\"");
      }
      sb.append(" src='");
      sb.append(resourceWriter.getResourcePath());
      sb.append("'");
      sb.append(macroParams.getImageStyle());
      sb.append("/>");
      sb.append("</div>");

      return sb.toString();
   }

   private final class MyPreprocessingContext implements PreprocessingContext {
      private final PageContext pageContext;

      /**
       * {@inheritDoc}
       */
      private MyPreprocessingContext(PageContext pageContext) {
         this.pageContext = pageContext;
      }

      /**
       * Returns the base URL from the global settings.
       * 
       * @return the base URL from the global settings.
       */
      public String getBaseUrl() {
         final String baseUrl = _settingsManager.getGlobalSettings().getBaseUrl();
         return baseUrl;
      }

      /**
       * {@inheritDoc}
       */
      public PageContext getPageContext() {
         return pageContext;
      }

      public SpaceManager getSpaceManager() {
         return _spaceManager;
      }

      /**
       * {@inheritDoc}
       */
      public PageManager getPageManager() {
         return _pageManager;
      }

      /**
       * {@inheritDoc}
       */
      public Map<String, ShortcutLinkConfig> getShortcutLinks() {
         return _shortcutLinksManager.getShortcutLinks();
      }
   }

   /**
    * Gets the UML source either from a Confluence page or from an attachment.
    */
   private final class UmlSourceLocatorConfluence implements UmlSourceLocator {
      private final PageContext _pageContext;

      /**
       * @param pageContext
       */
      private UmlSourceLocatorConfluence(PageContext pageContext) {
         _pageContext = pageContext;
      }

      public UmlSource get(String name) throws IOException {
         final ConfluenceLink.Parser parser = new ConfluenceLink.Parser(_pageContext, _spaceManager, _pageManager);
         final ConfluenceLink confluenceLink = parser.parse(name);

         if (logger.isDebugEnabled()) {
            logger.debug("Link '" + name + "' -> " + confluenceLink);
         }

         final Page page = _pageManager.getPage(confluenceLink.getSpaceKey(), confluenceLink.getPageTitle());
         // page cannot be null since it is validated before
         if (confluenceLink.hasAttachmentName()) {
            final Attachment attachment = page.getAttachmentNamed(confluenceLink.getAttachmentName());
            if (attachment == null) {
               throw new IOException("Cannot find attachment '" + confluenceLink.getAttachmentName()
                     + "' on page '" + confluenceLink.getPageTitle()
                     + "' in space '" + confluenceLink.getSpaceKey() + "'");
            }
            return new UmlSourceBuilder().append(attachment.getContentsAsStream()).build();

         } else {
            return new UmlSourceBuilder().append(page.getBodyAsStringWithoutMarkup()).build();
         }
      }
   }

   /**
    * Extension to {@link SourceStringReader} to add the function to get the image map for the diagram.
    */
   public static class MySourceStringReader extends SourceStringReader {
      /**
       * {@inheritDoc}
       */
      public MySourceStringReader(Defines defines, String source, List<String> config) {
         super(defines, source, config);
      }

      public ImageMap renderImage(OutputStream outputStream) throws IOException {
         final BlockUml blockUml = getBlocks().iterator().next();
         final PSystem system;
         try {
            system = blockUml.getSystem();
         } catch (InterruptedException e) {
            final IOException x = new IOException();
            x.initCause(e);
            throw x;
         }
         final StringBuilder cmap = new StringBuilder();
         system.exportDiagram(outputStream, cmap, 0, new FileFormatOption(FileFormat.PNG));
         return new ImageMap(cmap.toString());
      }
   }

}
