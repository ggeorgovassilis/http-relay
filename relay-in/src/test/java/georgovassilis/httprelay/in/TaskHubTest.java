package georgovassilis.httprelay.in;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import georgovassilis.httprelay.common.RequestTask;
import georgovassilis.httprelay.common.ResponseTask;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Unit test for TaskHubImpl
 * @author george georgovassilis
 *
 */
public class TaskHubTest {

	TaskHubImpl taskHubImpl;

	@Before
	public void setup() {
		taskHubImpl = new TaskHubImpl();
		taskHubImpl.setTimeout(500);
		taskHubImpl.setReady();
		taskHubImpl.start();
	}

	@After
	public void tearDown() {
		taskHubImpl.close();
	}

	@Test
	public void test_getNextRequestTask_on_empty_queue_returns_null() {
		RequestTask task = taskHubImpl.getNextRequestTask();
		assertNull(task);
	}

	@Test
	public void test_put_and_get_task() {
		RequestTask submittedTask = new RequestTask("GET", "http://localhost:8080");
		submittedTask.setId("1234");
		RequestCallback callback = new RequestCallback() {

			@Override
			public void onResponseReceived(ResponseTask response) {
			}

			@Override
			public void onError(Exception e) {
			}
		};
		taskHubImpl.submitRequestTask(submittedTask, callback);
		RequestTask task = taskHubImpl.getNextRequestTask();
		assertEquals(submittedTask, task);
	}

	@Test
	public void test_resolveTask() {
		RequestTask submittedTask = new RequestTask("GET", "http://localhost:8080");
		submittedTask.setId("1234");
		ResponseTask responseTask = new ResponseTask();
		responseTask.setId(submittedTask.getId());
		final AtomicReference<ResponseTask> resolvedResponseReference = new AtomicReference<>();

		RequestCallback callback = new RequestCallback() {

			@Override
			public void onResponseReceived(ResponseTask response) {
				resolvedResponseReference.set(response);
			}

			@Override
			public void onError(Exception e) {
			}
		};
		taskHubImpl.submitRequestTask(submittedTask, callback);
		RequestTask task = taskHubImpl.getNextRequestTask();
		taskHubImpl.resolveTask(task.getId(), responseTask);
		assertEquals(responseTask, resolvedResponseReference.get());
	}

	@Test
	public void test_timeout_full_task_queue() {
		int queueSize = taskHubImpl.getTaskCapacity();
		for (int i = 0; i < queueSize; i++) {
			RequestTask task = new RequestTask();
			task.setId("rt" + i);
			taskHubImpl.submitRequestTask(task, new RequestCallback() {

				@Override
				public void onResponseReceived(ResponseTask response) {
				}

				@Override
				public void onError(Exception e) {
				}
			});
		}
		RequestTask task = new RequestTask();
		task.setId("rt" + queueSize);
		try {
			taskHubImpl.submitRequestTask(task, new RequestCallback() {

				@Override
				public void onResponseReceived(ResponseTask response) {
				}

				@Override
				public void onError(Exception e) {
				}
			});
		} catch (RuntimeException e) {
			assertTrue(e.getMessage().contains("queue is full"));
		}

	}
}
