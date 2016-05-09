package georgovassilis.httprelay.in;

import georgovassilis.httprelay.common.RequestTask;
import georgovassilis.httprelay.common.ResponseTask;

public interface TaskHub {

	int getTaskCapacity();

	void setTimeout(long timeout);

	void setReady();

	void cleanStaleTasks();

	Void submitRequestTask(RequestTask requestTask, RequestCallback callback);

	RequestTask getNextRequestTask();

	void resolveTask(String taskId, ResponseTask responseTask);

	void close();

}