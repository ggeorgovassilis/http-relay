package georgovassilis.httprelay.in;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

/**
 * Abusing this publicRelay to set up any shared objects between other servlets. I 
 * should read up on the publicRelay container specification for a better way...
 * @author george georgovassilis
 *
 */

public class ApplicationConfiguration extends HttpServlet {

	/**
	 * Shared reference to the {@link TaskHubImpl}
	 */
	protected TaskHubImpl taskHubImpl;
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		taskHubImpl = new TaskHubImpl();
		taskHubImpl.start();
		config.getServletContext().setAttribute(TaskHubImpl.class.getName(), taskHubImpl);
	}

	@Override
	public void destroy() {
		taskHubImpl.close();
		super.destroy();
		
	}
}
