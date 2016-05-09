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
public class TaskHubImpl extends Thread implements TaskHub {

	enum State {
		initializing, active, shuttingDown, closed;
	}
	
	

	protected int capacity = 100;
	protected BlockingQueue<RequestTask> requests = new ArrayBlockingQueue<RequestTask>(capacity);
	protected Logger log = LogManager.getLogger(TaskHubImpl.class);
	protected long timeout = 60000;
	protected ConcurrentHashMap<String, TaskGroup> activeTasks = new ConcurrentHashMap<>();
	protected AtomicReference<State> state = new AtomicReference<TaskHubImpl.State>(State.initializing);
	
	/* (non-Javadoc)
	 * @see georgovassilis.httprelay.in.TaskHub#getTaskCapacity()
	 */
	@Override
	public int getTaskCapacity(){
		return capacity;
	}

	/* (non-Javadoc)
	 * @see georgovassilis.httprelay.in.TaskHub#setTimeout(long)
	 */
	@Override
	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	/* (non-Javadoc)
	 * @see georgovassilis.httprelay.in.TaskHub#setReady()
	 */
	@Override
	public void setReady() {
		synchronized (state) {
			state.set(State.active);
		}
	}

	/* (non-Javadoc)
	 * @see georgovassilis.httprelay.in.TaskHub#cleanStaleTasks()
	 */
	@Override
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

	/* (non-Javadoc)
	 * @see georgovassilis.httprelay.in.TaskHub#submitRequestTask(georgovassilis.httprelay.common.RequestTask, georgovassilis.httprelay.in.RequestCallback)
	 */
	@Override
	public Void submitRequestTask(final RequestTask requestTask, RequestCallback callback) {
		if (state.get() != State.active)
			return null;
		log.debug("Queueing task " + requestTask.getId() + " with content "
				+ (requestTask.getContent() == null ? 0 : requestTask.getContent().length));
		if (requestTask.getId()==null)
			throw new IllegalArgumentException("Request task ID is null, this will fail");
		TaskGroup group = new TaskGroup();
		group.setRequestTask(requestTask);
		group.setRequestCallback(callback);
		group.setCreated(new Date());
		synchronized (activeTasks) {
			activeTasks.put(requestTask.getId(), group);
		}
		try {
			boolean wasAdded = requests.offer(requestTask, timeout, TimeUnit.MILLISECONDS);
			if (!wasAdded)
				throw new RuntimeException("Couldn't process task "+requestTask.getId()+", task queue is full");
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see georgovassilis.httprelay.in.TaskHub#getNextRequestTask()
	 */
	@Override
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

	/* (non-Javadoc)
	 * @see georgovassilis.httprelay.in.TaskHub#resolveTask(java.lang.String, georgovassilis.httprelay.common.ResponseTask)
	 */
	@Override
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

	/* (non-Javadoc)
	 * @see georgovassilis.httprelay.in.TaskHub#close()
	 */
	@Override
	public void close() {
		log.debug("Terminating TaskHubImpl");
		synchronized (state) {
			state.set(State.shuttingDown);
			requests.clear();
			state.notifyAll();
		}
		try {
			log.debug("Waiting for TaskHubImpl thread to quit");
			join(timeout);
			log.debug("TaskHubImpl thread either quit or timed out");
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}
