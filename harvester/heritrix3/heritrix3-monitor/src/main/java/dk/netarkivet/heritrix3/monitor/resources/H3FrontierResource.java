package dk.netarkivet.heritrix3.monitor.resources;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.netarchivesuite.heritrix3wrapper.ScriptResult;

import com.antiaction.common.filter.Caching;
import com.antiaction.common.templateengine.TemplateBuilderFactory;

import dk.netarkivet.common.utils.Settings;
import dk.netarkivet.harvester.HarvesterSettings;
import dk.netarkivet.heritrix3.monitor.Heritrix3JobMonitor;
import dk.netarkivet.heritrix3.monitor.NASEnvironment;
import dk.netarkivet.heritrix3.monitor.NASUser;
import dk.netarkivet.heritrix3.monitor.ResourceAbstract;
import dk.netarkivet.heritrix3.monitor.ResourceManagerAbstract;

public class H3FrontierResource implements ResourceAbstract {

    private NASEnvironment environment;

    protected int R_FRONTIER = -1;
    
    @Override
    public void resources_init(NASEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public void resources_add(ResourceManagerAbstract resourceManager) {
        R_FRONTIER = resourceManager.resource_add(this, "/job/<numeric>/frontier/", false);
    }

    @Override
    public void resource_service(ServletContext servletContext, NASUser nas_user, HttpServletRequest req, HttpServletResponse resp, int resource_id, List<Integer> numerics, String pathInfo) throws IOException {
        if (NASEnvironment.contextPath == null) {
            NASEnvironment.contextPath = req.getContextPath();
        }
        if (NASEnvironment.servicePath == null) {
            NASEnvironment.servicePath = req.getContextPath() + req.getServletPath() + "/";
        }
        String method = req.getMethod().toUpperCase();
        if (resource_id == R_FRONTIER) {
            if ("GET".equals(method) || "POST".equals(method)) {
                frontier_list(req, resp, numerics);
            }
        }
    }

    public void frontier_list(HttpServletRequest req, HttpServletResponse resp, List<Integer> numerics) throws IOException {
        Locale locale = resp.getLocale();
        resp.setContentType("text/html; charset=UTF-8");
        ServletOutputStream out = resp.getOutputStream();
        Caching.caching_disable_headers(resp);

        TemplateBuilderFactory<MasterTemplateBuilder> masterTplBuilderFactory = TemplateBuilderFactory.getInstance(environment.templateMaster, "master.tpl", "UTF-8", MasterTemplateBuilder.class);
        MasterTemplateBuilder masterTplBuilder = masterTplBuilderFactory.getTemplateBuilder();

        StringBuilder sb = new StringBuilder();
        StringBuilder menuSb = new StringBuilder();

        String regex = req.getParameter("regex");
        if (regex == null || regex.length() == 0) {
            regex =".*";
        }
        long limit = 1000;
        String limitStr = req.getParameter("limit");
        if (limitStr != null && limitStr.length() > 0) {
            try {
                limit = Long.parseLong(limitStr);
            } catch (NumberFormatException e) {
            }
        }
        String initials = req.getParameter("initials");
        if (initials == null) {
            initials = "";
        }

        String script = environment.NAS_GROOVY_SCRIPT;

        String deleteStr = req.getParameter("delete");
        if (deleteStr != null && "1".equals(deleteStr) && initials != null && initials.length() > 0) {
            script += "\n";
            script += "\ninitials = \"" + initials + "\"";
            script += "\ndeleteFromFrontier '" + regex + "'\n";
        } else {
            script += "\n";
            script += "\nlistFrontier '" + regex + "', " + limit + "\n";
        }

        // To use, just remove the initial "//" from any one of these lines.
        //
        //killToeThread  1       //Kill a toe thread by number
        //listFrontier '.*stats.*'    //List uris in the frontier matching a given regexp
        //deleteFromFrontier '.*foobar.*'    //Remove uris matching a given regexp from the frontier
        //printCrawlLog '.*'          //View already crawled lines uris matching a given regexp

        long jobId = numerics.get(0);
        Heritrix3JobMonitor h3Job = environment.h3JobMonitorThread.getRunningH3Job(jobId);

        if (h3Job != null && h3Job.isReady()) {
            menuSb.append("<tr><td>&nbsp; &nbsp; &nbsp; <a href=\"");
            menuSb.append(NASEnvironment.servicePath);
            menuSb.append("job/");
            menuSb.append(h3Job.jobId);
            menuSb.append("/");
            menuSb.append("\"> Job ");
            menuSb.append(h3Job.jobId);
            menuSb.append("</a></td></tr>");

            if (deleteStr != null && "1".equals(deleteStr) && (initials == null || initials.length() == 0)) {
                //sb.append("<span style=\"text-color: red;\">Initials required to delete from the frontier queue!</span><br />\n");
                sb.append("<div class=\"notify notify-red\"><span class=\"symbol icon-error\"></span> Initials required to delete from the frontier queue!</div>");
            }

            sb.append("<form class=\"form-horizontal\" action=\"?\" name=\"insert_form\" method=\"post\" enctype=\"application/x-www-form-urlencoded\" accept-charset=\"utf-8\">\n");
            sb.append("<label for=\"limit\">Lines to show:</label>");
            sb.append("<input type=\"text\" id=\"limit\" name=\"limit\" value=\"" + limit + "\" placeholder=\"return limit\">\n");
            sb.append("<label for=\"regex\">Filter regex:</label>");
            sb.append("<input type=\"text\" id=\"regex\" name=\"regex\" value=\"" + regex + "\" placeholder=\"regex\" style=\"display:inline;width:350px;\">\n");
            sb.append("<button type=\"submit\" name=\"show\" value=\"1\" class=\"btn btn-success\"><i class=\"icon-white icon-thumbs-up\"></i> Show</button>\n");
            sb.append("&nbsp;");
            sb.append("<label for=\"initials\">User initials:</label>");
            sb.append("<input type=\"text\" id=\"initials\" name=\"initials\" value=\"" + initials  + "\" placeholder=\"initials\">\n");
            sb.append("<button type=\"submit\" name=\"delete\" value=\"1\" class=\"btn btn-success\"><i class=\"icon-white icon-thumbs-up\"></i> Delete</button>\n");
            sb.append("</form>\n");

            ScriptResult scriptResult = h3Job.h3wrapper.ExecuteShellScriptInJob(h3Job.jobResult.job.shortName, "groovy", script);
            //System.out.println(new String(scriptResult.response, "UTF-8"));
            if (scriptResult != null && scriptResult.script != null) {
                if (scriptResult.script.htmlOutput != null) {
                    sb.append("<fieldset><!--<legend>htmlOut</legend>-->");
                    sb.append(scriptResult.script.htmlOutput);
                    sb.append("</fieldset><br />\n");
                }
                if (scriptResult.script.rawOutput != null) {
                    sb.append("<fieldset><!--<legend>rawOut</legend>-->");
                    sb.append("<pre>");
                    sb.append(scriptResult.script.rawOutput);
                    sb.append("</pre>");
                    sb.append("</fieldset><br />\n");
                }
            }
        } else {
            sb.append("Job ");
            sb.append(jobId);
            sb.append(" is not running.");
        }

        masterTplBuilder.insertContent("Job " + jobId + " Frontier", menuSb.toString(), environment.generateLanguageLinks(locale),
        		"Job " + jobId + " Frontier", sb.toString(),
        		"<meta http-equiv=\"refresh\" content=\""+Settings.get(HarvesterSettings.HARVEST_MONITOR_REFRESH_INTERVAL)+"\"/>\n").write(out);

        out.flush();
        out.close();
    }
    
}