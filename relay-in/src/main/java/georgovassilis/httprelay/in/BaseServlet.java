package georgovassilis.httprelay.in;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Base class for servlets which contributes shared objects such as loggers and the {@link TaskHub}
 * @author george georgovassilis
 *
 */

public abstract class BaseServlet extends HttpServlet{

	protected TaskHub taskHub;
	protected Logger log;


	@Override
	public void init(javax.servlet.ServletConfig config) throws ServletException {
		log = LogManager.getLogger(getClass().getName());
		taskHub = (TaskHub)config.getServletContext().getAttribute(TaskHub.class.getName());
		log.info("Initialized "+getClass().getName());
	};

}
