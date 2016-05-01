package georgovassilis.httprelay.in;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import georgovassilis.httprelay.common.RequestTask;
import georgovassilis.httprelay.common.ResponseTask;

/**
 * The private relay (long-)polls for {@link RequestTask}s with an HTTP GET request
 * and answers back with a {@link ResponseTask} through an HTTP POST request.
 * Unfortunately it is not possible to combine those two requests into one because some HTTP servers/proxies/
 * servlet containers won't allow the private relay to post data over the same HTTP connection after it has received
 * the {@link RequestTask}, so it has to perform a second HTTP request with the {@link ResponseTask} data.
 * @author george georgovassilis
 *
 */
public class TaskHubServlet extends BaseServlet {

	ObjectMapper mapper = new ObjectMapper();
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		new Thread(){
			@Override
			public void run() {
				try {
					sleep(5000);
				} catch (InterruptedException e) {
				}
				taskHub.setReady(true);
			}
		}.start();
	}

	protected void tellServerToComeBackLater(HttpServletResponse resp) throws ServletException, IOException {
		log.info("Telling server that there are no new tasks");
		resp.sendError(HttpServletResponse.SC_NO_CONTENT, "no tasks");
	}

	protected void giveTaskToServer(RequestTask requestTask, HttpServletResponse resp) throws ServletException, IOException {
		log.info("Replying with next task " + requestTask.getId() + " to server");
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		mapper.writeValue(resp.getOutputStream(), requestTask);
		byte[] bytes = baos.toByteArray();
		resp.setStatus(200);
		resp.setContentLength(bytes.length);
		resp.setContentType("application/json; charset=utf-8");
		resp.getOutputStream().write(bytes);
		resp.getOutputStream().flush();
	}

	protected ResponseTask getResponseTask(HttpServletRequest req) throws ServletException, IOException {
		ResponseTask responseTask = mapper.readValue(req.getInputStream(), ResponseTask.class);
		return responseTask;
	}

	/**
	 * The server-side relay GETs tasks with this method 
	 */
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		log.info("Server asked for next task");
		RequestTask requestTask = taskHub.getNextRequestTask();
		if (requestTask == null)
			tellServerToComeBackLater(resp);
		else
			giveTaskToServer(requestTask, resp);
	}

	/**
	 * The server-side relay POSTs completed tasks with this method
	 */
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		log.info("Server replies with processed task");
		ResponseTask responseTask = getResponseTask(req);
		log.info("Server replies with processed task " + responseTask.getId());
		taskHub.resolveTask(responseTask.getId(), responseTask);
	}
}
