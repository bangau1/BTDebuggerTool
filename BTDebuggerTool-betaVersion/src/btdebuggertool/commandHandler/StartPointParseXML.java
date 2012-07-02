package btdebuggertool.commandHandler;

import java.io.InputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.IConsoleView;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.handlers.HandlerUtil;

import bt.model.BTParser;
import bt.model.BTTree;
import btdebuggertool.perspective.DebuggerPerspective;
import btdebuggertool.simulator.BTSimulator;
import btdebuggertool.simulator.GlobalData;
import btdebuggertool.view.StateVarDebuggerView;
import btdebuggertool.view.ZestDebuggerView;


public class StartPointParseXML extends AbstractHandler implements IHandler {
	private static final String DEBUGGER_PERSPECTIVE_ID = "btdebuggertool.perspective.DebuggerPerspective";
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IStructuredSelection selectedFiles = (IStructuredSelection)HandlerUtil.getActiveMenuSelection(event);
		final StringBuffer buf = new StringBuffer();
		final Object selectedFile = selectedFiles.getFirstElement();
		final IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
		
		BusyIndicator.showWhile(Display.getDefault(), new Runnable() {
			
			@Override
			public void run() {
				boolean isValid = false;
				if(selectedFile instanceof IFile){
					IFile xml = (IFile)selectedFile;
					try {
						InputStream is = xml.getContents(true);
						
						GlobalData.tree = parseXmlToBT(is);
						System.out.println(GlobalData.tree.toString());
						is.close();
						isValid = true;
					} catch (Exception e) {
						e.printStackTrace();
						buf.append(e.getMessage());
						isValid = false;
					}
					
				}else{
					isValid = false;
				}
				
				if(!isValid){
					Display.getDefault().syncExec(new Runnable() {
						
						@Override
						public void run() {
							String message = buf.toString();
							if(message.length() > 0)
								MessageDialog.openError(Display.getDefault().getActiveShell(), "Error", buf.toString());
							else
								MessageDialog.openError(Display.getDefault().getActiveShell(), "Error", "The selected file is not xml");
						}
					});
				}else{
					try {

						//showing the console view
						IWorkbenchPage perspectivePage = PlatformUI.getWorkbench().showPerspective(DEBUGGER_PERSPECTIVE_ID, window);// showPerspective(DEBUGGER_PERSPECTIVE_ID, window);
						IConsole myConsole = findConsole("btdebuggertool.view.consoleView");
						IConsoleView consoleView = (IConsoleView) perspectivePage.showView(IConsoleConstants.ID_CONSOLE_VIEW);
						consoleView.display(myConsole);
						
						//getting the output stream for the writing purpose
						((MessageConsole)myConsole).clearConsole();
						MessageConsoleStream out = ((MessageConsole)myConsole).newMessageStream();
						
						BTSimulator simulatorData = new BTSimulator(GlobalData.tree, out);
						simulatorData.init();
						
						IViewPart zestViewPart = perspectivePage.showView(DebuggerPerspective.ZEST_VIEW_ID);
						if(zestViewPart!=null && zestViewPart instanceof ZestDebuggerView){
							ZestDebuggerView zestView = (ZestDebuggerView)zestViewPart;
							zestView.setInput(simulatorData);
						}
						
						IViewPart stateVarViewPart = perspectivePage.showView(StateVarDebuggerView.ID);
						if(stateVarViewPart!=null && stateVarViewPart instanceof StateVarDebuggerView){
							StateVarDebuggerView stateVarView = (StateVarDebuggerView)stateVarViewPart;
							stateVarView.setInput(simulatorData);
						}
					} catch (WorkbenchException e) {
						e.printStackTrace();
						
					}
				}
				

			}
		});

		return null;
	}
	
	private BTTree parseXmlToBT(final InputStream is) throws Exception{
		SAXParserFactory factory = SAXParserFactory.newInstance();
		
		// Set up output stream
        String modName="";
        boolean trace = false;
        
		// Parse the input
		SAXParser saxParser = factory.newSAXParser();
		BTParser bp = new BTParser(modName,trace);
		saxParser.parse(is, bp);
		return bp.getBt();

		
	}
	
	private MessageConsole findConsole(String name) {
	      ConsolePlugin plugin = ConsolePlugin.getDefault();
	      IConsoleManager conMan = plugin.getConsoleManager();
	      IConsole[] existing = conMan.getConsoles();
	      for (int i = 0; i < existing.length; i++)
	         if (name.equals(existing[i].getName()))
	            return (MessageConsole) existing[i];
	      //no console found, so create a new one
	      MessageConsole myConsole = new MessageConsole(name, null);
	      conMan.addConsoles(new IConsole[]{myConsole});
	      return myConsole;
	}
}
