package georgovassilis.httprelay.in;

import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import georgovassilis.httprelay.common.RequestTask;
import georgovassilis.httprelay.common.ResponseTask;

/**
 * Receives {@link RequestTask}s from the {@link PublicRelayServlet} and stores
 * them (in memory) for the private relay. The {@link TaskHubServlet} picks
 * tasks one by one and puts back server responses.
 * 
 * @author george georgovassilis
 *
 */
public class TaskHub extends Thread {

	enum State {
		initializing, active, shuttingDown, closed;
	}

	// TODO: clean outdated tasks
	protected BlockingQueue<RequestTask> requests = new ArrayBlockingQueue<RequestTask>(100);
	protected Logger log = LogManager.getLogger(TaskHub.class);
	protected long timeout = 60000;
	protected ConcurrentHashMap<String, TaskGroup> activeTasks = new ConcurrentHashMap<>();
	protected AtomicReference<State> state = new AtomicReference<TaskHub.State>(State.initializing);

	public void setReady() {
		synchronized (state) {
			state.set(State.active);
		}
	}

	public void cleanStaleTasks() {
		Date now = new Date();
		synchronized (activeTasks) {
			log.debug("Looking for stale tasks in " + activeTasks.size() + " active tasks");
			for (Iterator<String> ite = activeTasks.keySet().iterator(); ite.hasNext();) {
				String id = ite.next();
				TaskGroup group = activeTasks.get(id);
				Date timeOutForGroup = new Date(group.getCreated().getTime() + timeout);
				if (timeOutForGroup.before(now)) {
					try {
						log.warn("Closing stale task " + id);
						resolveTask(id, null);
					} catch (Exception e) {
					}
				}
			}
		}
	}

	public void submitRequestTask(final RequestTask requestTask, RequestCallback callback) {
		if (state.get() != State.active)
			return;
		log.debug("Queueing task " + requestTask.getId() + " with content "
				+ (requestTask.getContent() == null ? 0 : requestTask.getContent().length));
		TaskGroup group = new TaskGroup();
		group.setRequestTask(requestTask);
		group.setRequestCallback(callback);
		group.setCreated(new Date());
		synchronized (activeTasks) {
			activeTasks.put(requestTask.getId(), group);
		}
		try {
			requests.put(requestTask);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public RequestTask getNextRequestTask() {
		try {
			log.debug("Waiting for next request task");
			if (state.get() != State.active)
				return null;
			return requests.poll(timeout, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public void resolveTask(String taskId, ResponseTask responseTask) {
		TaskGroup group = null;
		synchronized (activeTasks) {
			group = activeTasks.remove(taskId);
		}
		synchronized (group) {
			group.setResponseTask(responseTask);
			group.getRequestCallback().onResponseReceived(responseTask);
		}
	}

	@Override
	public void run() {
		while (true) {
			synchronized (state) {
				if (state.get() == State.shuttingDown)
					return;
			}
			try {
				cleanStaleTasks();
				sleep(timeout);
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	public void close() {
		synchronized (state) {
			state.set(State.shuttingDown);
			requests.clear();
			state.notifyAll();
		}
		try {
			join();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}
