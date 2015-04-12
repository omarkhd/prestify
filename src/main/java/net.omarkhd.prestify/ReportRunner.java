package net.omarkhd.prestify;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.io.IOException;
import java.net.URL;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.pentaho.reporting.engine.classic.core.MasterReport;
import org.pentaho.reporting.engine.classic.core.ReportProcessingException;
import org.pentaho.reporting.engine.classic.core.modules.output.table.rtf.RTFReportUtil;
import org.pentaho.reporting.engine.classic.core.modules.output.table.xls.ExcelReportUtil;
import org.pentaho.reporting.engine.classic.core.modules.output.pageable.pdf.PdfReportUtil;
import org.pentaho.reporting.engine.classic.core.modules.output.table.base.StreamReportProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.table.html.AllItemsHtmlPrinter;
import org.pentaho.reporting.engine.classic.core.modules.output.table.html.FileSystemURLRewriter;
import org.pentaho.reporting.engine.classic.core.modules.output.table.html.HtmlPrinter;
import org.pentaho.reporting.engine.classic.core.modules.output.table.html.StreamHtmlOutputProcessor;
import org.pentaho.reporting.libraries.repository.ContentIOException;
import org.pentaho.reporting.libraries.repository.ContentLocation;
import org.pentaho.reporting.libraries.repository.DefaultNameGenerator;
import org.pentaho.reporting.libraries.repository.file.FileRepository;
import org.pentaho.reporting.libraries.resourceloader.Resource;
import org.pentaho.reporting.libraries.resourceloader.ResourceException;
import org.pentaho.reporting.libraries.resourceloader.ResourceManager;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

@WebServlet("/reports/*")
public class ReportRunner extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private String reportsBaseDir;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
		throws ServletException, IOException {

		ArrayList<String> formats = new ArrayList<String>(4);
		formats.add("pdf");
		formats.add("html");
		formats.add("rtf");
		formats.add("xls");
		formats.add("xlsx");

		String format = (request.getParameter("format") + "").toLowerCase();
		String payload = request.getParameter("parameters");

		try {
			response.setStatus(HttpServletResponse.SC_OK);
			Map parameters = null;

			if(!formats.contains(format)) {
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				throw new Exception("Invalid report output format" );
			}

			if(payload != null) {
				String b64decoded = new String(java.util.Base64.getDecoder().decode(payload.getBytes()));
				parameters = (JSONObject) JSONValue.parse(b64decoded);

				if(parameters == null) {
					response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
					throw new Exception("Malformed JSON parameters");
				}
			}

			String path = request.getPathInfo();
			if(path == null || path.equals("/")) {
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				throw new Exception("You have to specify a report's name or path");
			}

			this.doReport(path, format, parameters, response);
		}
		catch(Exception e) {
			response.setHeader("Content-Type", "application/json; charset=UTF-8");
			/**
			 * if at this point the response still has a success http code, we have to
			 * set a general error code to denote an error has occured
			 */
			if(response.getStatus() >= 200 && response.getStatus() < 300)
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

			JSONObject body = new JSONObject();
			body.put("message", e.getMessage());
			response.getWriter().write(body.toString());
		}
	}

	private String getReportsBaseDir() {
		if(this.reportsBaseDir == null) {
			this.reportsBaseDir = this.getServletContext().getInitParameter("reports.home");

			if(this.reportsBaseDir == null || this.reportsBaseDir.trim().equals("")) {
				this.reportsBaseDir = System.getProperty("user.home");
			}
		}

		return this.reportsBaseDir;
	}

	private void doReport(String path, String format, Map parameters, HttpServletResponse response)
		throws ReportProcessingException, IOException, ResourceException, ContentIOException {

		ResourceManager manager = new ResourceManager();
		manager.registerDefaults();

		URL report_url = new URL("file://" + this.getReportsBaseDir() + path + ".prpt");
		Resource resource = manager.createDirectly(report_url, MasterReport.class);
		MasterReport report = (MasterReport) resource.getResource();

		if(parameters != null) {
			for(Object entry: parameters.entrySet()) {
				String key = (String) ((Map.Entry) entry).getKey();
				Object value = ((Map.Entry) entry).getValue();
				report.getParameterValues().put(key, this.fixOrGetParameterValue(value));
			}
		}
		/**
		 * this temp output stream is used to hold the report bytes before it is written
		 * to the http reponse output stream, because if there is an error in the generation we shall
		 * not be able to display it
		 */
		ByteArrayOutputStream tmpstream = new ByteArrayOutputStream();

		if(format.equals("pdf")) {
			response.setContentType("application/pdf");
			PdfReportUtil.createPDF(report, tmpstream);
			tmpstream.writeTo(response.getOutputStream());
		}
		else if(format.equals("rtf")) {
			response.setContentType("application/rtf");
			RTFReportUtil.createRTF(report, tmpstream);
			tmpstream.writeTo(response.getOutputStream());
		}
		else if(format.equals("xls")) {
			response.setContentType("application/vnd.ms-excel");
			ExcelReportUtil.createXLS(report, tmpstream);
			tmpstream.writeTo(response.getOutputStream());
		}
		else if(format.equals("xlsx")) {
			response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet ");
			ExcelReportUtil.createXLSX(report, tmpstream);
			tmpstream.writeTo(response.getOutputStream());
		}
		else if(format.equals("html")) {
			response.setContentType("text/html");
			String filename = "index.html";
			File folder = new File(this.getServletContext().getRealPath("/out/" + System.currentTimeMillis()));

			if(!folder.exists()) {
				folder.mkdirs();

				final FileRepository repository = new FileRepository(folder);
				final ContentLocation location = repository.getRoot();
				final HtmlPrinter printer = new AllItemsHtmlPrinter(report.getResourceManager());

				printer.setContentWriter(location, new DefaultNameGenerator(location, filename));
				printer.setDataWriter(location, new DefaultNameGenerator(location, "content"));
				printer.setUrlRewriter(new FileSystemURLRewriter());

				final StreamHtmlOutputProcessor html_processor = new StreamHtmlOutputProcessor(report.getConfiguration());
				html_processor.setPrinter(printer);

				final StreamReportProcessor processor = new StreamReportProcessor(report, html_processor);
				processor.processReport();
				processor.close();

				response.setStatus(HttpServletResponse.SC_CREATED);
				response.setHeader("Location", "/out/" + folder.getName() + "/" + filename);
				//response.sendRedirect(route);
			}
		}
	}

	private Object fixOrGetParameterValue(Object parameter) {
		if(parameter instanceof Long) {
			if((Long) parameter <= Integer.MAX_VALUE)
				parameter = ((Long) parameter).intValue();
		}

		return parameter;
	}

	private void setNotAllowed(HttpServletResponse response) {
		response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response)
		throws ServletException, IOException {

		this.setNotAllowed(response);
	}

	protected void doPut(HttpServletRequest request, HttpServletResponse response)
		throws ServletException, IOException {

		this.setNotAllowed(response);
	}

	protected void doDelete(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		this.setNotAllowed(response);
	}
}