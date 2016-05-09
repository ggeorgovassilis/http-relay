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
 * publicRelay containers won't allow the private relay to post data over the same HTTP connection after it has received
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
				taskHub.setReady();
			}
		}.start();
	}

	protected void tellServerToComeBackLater(HttpServletResponse resp) throws ServletException, IOException {
		log.debug("No new tasks for private relay");
		resp.sendError(HttpServletResponse.SC_NO_CONTENT, "no tasks");
	}

	protected void giveTaskToServer(RequestTask requestTask, HttpServletResponse resp) throws ServletException, IOException {
		log.debug("Giving task " + requestTask.getId() + " to private relay");
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		mapper.writeValue(resp.getOutputStream(), requestTask);
		byte[] bytes = baos.toByteArray();
		resp.setStatus(200);
		resp.setContentLength(bytes.length);
		resp.setContentType("application/json; charset=utf-8");
		resp.getOutputStream().write(bytes);
		resp.getOutputStream().flush();
	}

	protected ResponseTask getResponseTaskFromHttpRequest(HttpServletRequest req) throws ServletException, IOException {
		ResponseTask responseTask = mapper.readValue(req.getInputStream(), ResponseTask.class);
		return responseTask;
	}
	
	/**
	 * The server-side relay GETs tasks with this method 
	 */
	protected void giveTaskToPrivateRelay(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		log.debug("Private relay asked for next task");
		RequestTask requestTask = taskHub.getNextRequestTask();
		if (requestTask == null)
			tellServerToComeBackLater(resp);
		else
			giveTaskToServer(requestTask, resp);
	}

	/**
	 * The server-side relay POSTs completed tasks with this method
	 */
	protected void acceptResponseFromPrivateRelay(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		ResponseTask responseTask = getResponseTaskFromHttpRequest(req);
		log.debug("Private relay submits processed task " + responseTask.getId());
		taskHub.resolveTask(responseTask.getId(), responseTask);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		giveTaskToPrivateRelay(req, resp);
	}
	

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		acceptResponseFromPrivateRelay(req, resp);
	}
}
