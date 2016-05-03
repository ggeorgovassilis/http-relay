package georgovassilis.httprelay.in;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import georgovassilis.httprelay.common.RequestTask;
import georgovassilis.httprelay.common.ResponseTask;

/**
 * Receives {@link RequestTask}s from the {@link PublicRelayServlet} and stores them (in memory)
 * for the private relay. The {@link TaskHubServlet} picks tasks one by one and puts back server responses.
 * @author george georgovassilis
 *
 */
public class TaskHub {

	//TODO: clean outdated tasks
	protected BlockingQueue<RequestTask> requests = new ArrayBlockingQueue<RequestTask>(100);
	protected Logger log = LogManager.getLogger(TaskHub.class);
	protected long timeout = 60000;
	protected boolean ready = false;
	protected ConcurrentHashMap<String, RequestTask> activeTasks = new ConcurrentHashMap<>();

	public void setReady(boolean value) {
		this.ready = value;
	}

	public CompletableFuture<ResponseTask> submitRequestTask(final RequestTask requestTask) {
		if (!ready)
			return null;
		log.info("Queueing task " + requestTask.getId() + " with content "
				+ (requestTask.getContent() == null ? 0 : requestTask.getContent().length));
		activeTasks.put(requestTask.getId(), requestTask);
		CompletableFuture<ResponseTask> cf = CompletableFuture.supplyAsync(() -> {
			synchronized (requestTask) {
				try {
					requests.put(requestTask);
					requestTask.wait(timeout);
				} catch (InterruptedException e) {
				}
				ResponseTask responseTask = requestTask.getResponse();
				if (responseTask == null) {
					log.error("Task " + requestTask.getId() + " timed out");
					responseTask = new ResponseTask();
					responseTask.setStatus(HttpServletResponse.SC_NO_CONTENT);
					responseTask.setContent("Request to backend timed out".getBytes());
					requests.remove(requestTask);
				} else
					log.info("Task " + requestTask.getId() + " returned with "
							+ (responseTask.getContent() == null ? 0 : responseTask.getContent().length) + " bytes");
				return responseTask;
			}
		});
		return cf;
	}

	public RequestTask getNextRequestTask() {
		try {
			if (!ready)
				return null;
			return requests.poll(timeout, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public void resolveTask(String taskId, ResponseTask responseTask) {

		RequestTask requestTask = activeTasks.get(taskId);
		synchronized (requestTask) {
			requestTask.setResponse(responseTask);
			requestTask.notifyAll();
		}
	}
	
	public void close(){
		requests.clear();
	}
}
