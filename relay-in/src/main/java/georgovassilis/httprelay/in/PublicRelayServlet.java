package georgovassilis.httprelay.in;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;

import georgovassilis.httprelay.common.RequestTask;
import georgovassilis.httprelay.common.ResponseTask;

/**
 * Servlet which accepts HTTP requests from browsers and puts them into a queue (the {@link TaskHub})
 * from where the private relay draws tasks. The private relay also puts responses into the queue which
 * the {@link PublicRelayServlet} sends back to the browser.
 * @author george georgovassilis
 *
 */

public class PublicRelayServlet extends BaseServlet {

	/**
	 * API adapter which gets HTTP headers as a map
	 * @param req
	 * @return
	 */
	protected Map<String, String> getHeaders(HttpServletRequest req) {
		Enumeration<String> headerNames = req.getHeaderNames();
		Map<String, String> headers = new HashMap<String, String>();
		while (headerNames.hasMoreElements()) {
			String header = headerNames.nextElement();
			headers.put(header, req.getHeader(header));
		}
		return headers;
	}

	/**
	 * API adapter which gets HTTP parameters as a map
	 * @param req
	 * @return
	 */
	protected Map<String, String> getParameters(HttpServletRequest req) {
		Enumeration<String> parameterNames = req.getParameterNames();
		Map<String, String> parameters = new HashMap<String, String>();
		while (parameterNames.hasMoreElements()) {
			String parameter = parameterNames.nextElement();
			parameters.put(parameter, req.getParameter(parameter));
		}
		return parameters;
	}

	/**
	 * API adapter which an HTTP request's contents as a byte array
	 * @param req
	 * @return
	 */
	protected byte[] getContent(HttpServletRequest req) throws IOException {
		//TODO: fail on excessively large requests
		ServletInputStream in = req.getInputStream();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		IOUtils.copy(in, baos);
		return baos.toByteArray();
	}

	/**
	 * Convert an {@link HttpServletRequest} into a {@link RequestTask}
	 * @param req
	 * @return
	 * @throws IOException
	 */
	protected RequestTask packRequestIntoTask(HttpServletRequest req) throws IOException {
		RequestTask requestTask = new RequestTask();
		requestTask.setId(UUID.randomUUID().toString());
		String url = req.getPathInfo() + (req.getQueryString() != null ? "?" + req.getQueryString() : "");
		requestTask.setUrl(url);
		requestTask.setMethod(req.getMethod());
		requestTask.setHeaders(getHeaders(req));
		requestTask.setParameters(getParameters(req));

		if (requestTask.getMethod().equalsIgnoreCase("PUT") || requestTask.getMethod().equalsIgnoreCase("POST"))
			requestTask.setContent(getContent(req));
		return requestTask;
	}

	/**
	 * Copy data from a {@link ResponseTask} into an {@link HttpServletResponse}
	 * @param task
	 * @param resp
	 * @throws IOException
	 */
	void packTaskIntoResponse(ResponseTask task, HttpServletResponse resp) throws IOException {
		resp.setStatus(task.getStatus());
		for (String headerName : task.getHeaders().keySet()) {
			resp.addHeader(headerName, task.getHeaders().get(headerName));
		}
		if (task.getContent() != null) {
			ServletOutputStream out = resp.getOutputStream();
			out.write(task.getContent());
			out.flush();
		}
	}

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		log.info("Accepted request from " + req.getRemoteAddr() + " " + req.getMethod() + " " + req.getRequestURL());
		RequestTask requestTask = packRequestIntoTask((HttpServletRequest) req);
		CompletableFuture<ResponseTask> responseTask = taskHub.submitRequestTask(requestTask);
		if (responseTask==null){
			log.info("Disregarding request, application isn't fully loaded yet");
			return;
		}
		AsyncContext context = req.startAsync();
		responseTask.thenAccept(response -> {
			try {
				packTaskIntoResponse(response, (HttpServletResponse) context.getResponse());
			} catch (Exception e) {
				log.error(e.getMessage(), e);
				throw new RuntimeException(e);
			}
			finally{
				context.complete();
			}
		});

	}

}
