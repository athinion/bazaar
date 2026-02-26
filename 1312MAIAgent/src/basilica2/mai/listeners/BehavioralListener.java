package basilica2.mai.listeners;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import edu.cmu.cs.lti.basilica2.core.Event;
import basilica2.agents.components.InputCoordinator;
import basilica2.agents.events.MessageEvent;
import basilica2.agents.events.PresenceEvent;
import basilica2.agents.listeners.BasilicaPreProcessor;
//import basilica2.social.listeners.ActivityTracker;
import basilica2.agents.listeners.BasilicaAdapter;
import edu.cmu.cs.lti.project911.utils.time.TimeoutReceiver;
import basilica2.mai.events.MAITriggerEvent;
import basilica2.agents.components.StateMemory;
import edu.cmu.cs.lti.basilica2.core.Agent;
import edu.cmu.cs.lti.project911.utils.log.Logger;

import basilica2.agents.data.RollingWindow;



public class BehavioralListener implements BasilicaPreProcessor {


	// Configure RollingWindow history size + purge interval

	protected static final int HISTORY_WINDOW = 300; // define history window
    private static final String TRIGGER_NAME = "BEHAVIORAL";

	public BehavioralListener(Agent a) {
		RollingWindow.sharedWindow().setWindowSize(HISTORY_WINDOW, 2);
	}
	//Agent a = overmind.getAgent();
	//ActivityTracker at = new ActivityTracker();


	// we need the behavioral listener to take info from the presence watcher and calculate messages per student


	@Override
	public void preProcessEvent(InputCoordinator source, Event event)
	{

		if (!(event instanceof MessageEvent)) {
            return;
        }
		MessageEvent me = (MessageEvent)event;
		
	}

	
	/**
	 * @return the classes of events that this Preprocessor cares about
	 */
	@Override
	public Class[] getPreprocessorEventClasses()
	{
		//MessageEvents and PresenceEvents will be delivered to this watcher.
		return new Class[]{MessageEvent.class, PresenceEvent.class};
	}

}
