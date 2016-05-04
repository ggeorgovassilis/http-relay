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
	protected ConcurrentHashMap<String, TaskGroup> activeTasks = new ConcurrentHashMap<>();

	public void setReady(boolean value) {
		this.ready = value;
	}

	public void submitRequestTask(final RequestTask requestTask, RequestCallback callback) {
		if (!ready)
			return;
		log.info("Queueing task " + requestTask.getId() + " with content "
				+ (requestTask.getContent() == null ? 0 : requestTask.getContent().length));
		TaskGroup group = new TaskGroup();
		group.setRequestTask(requestTask);
		group.setRequestCallback(callback);
		activeTasks.put(requestTask.getId(), group);
		try {
			requests.put(requestTask);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
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

		TaskGroup group = activeTasks.remove(taskId);
		synchronized (group) {
			group.setResponseTask(responseTask);
			group.getRequestCallback().onResponseReceived(responseTask);
		}
	}
	
	public void close(){
		requests.clear();
	}
}
