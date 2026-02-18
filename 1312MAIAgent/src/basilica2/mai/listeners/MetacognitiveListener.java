package basilica2.mai.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edu.cmu.cs.lti.basilica2.core.Event;
import basilica2.agents.components.InputCoordinator;
import basilica2.agents.events.MessageEvent;
import basilica2.agents.listeners.BasilicaPreProcessor;
import basilica2.mai.events.MAITriggerEvent;
import basilica2.agents.components.StateMemory;
import edu.cmu.cs.lti.basilica2.core.Agent;
import edu.cmu.cs.lti.project911.utils.log.Logger;

import basilica2.agents.data.RollingWindow;


/**
 * Cognitive Actor for MAI F2F
 * 
 * Triggers when: (DOM is High) AND (CON is High) AND (FCD is Medium OR FCD is High)
 * Blackout: 180 seconds
 */
public class MetacognitiveListener implements BasilicaPreProcessor {


	// Configure RollingWindow history size + purge interval

	protected static final int HISTORY_WINDOW = 300; // define history window
	
    //private static final int TRIGGER_THRESHOLD = 3;
    private static final String TRIGGER_NAME = "METACOGNITIVE";
    //private static final double PRIORITY = 2.0; // Priority level
	
	// test comment for eclipse

	public MetacognitiveListener(Agent a) {
		RollingWindow.sharedWindow().setWindowSize(HISTORY_WINDOW, 2);
	}



	/**
	 * @param source the InputCoordinator - to push new events to. (Modified events don't need to be re-pushed).
	 * @param event an incoming event which matches one of this preprocessor's advertised classes (see getPreprocessorEventClasses)
	 * 
	 * Preprocess an incoming event, by modifying this event or creating a new event in response. 
	 * All original and new events will be passed by the InputCoordinator to the second-stage Reactors ("BasilicaListener" instances).
	 */
	@Override
	public void preProcessEvent(InputCoordinator source, Event event)
	{

		if (!(event instanceof MessageEvent)) {
            return;
        }
		MessageEvent me = (MessageEvent)event;
		
		
		// this was made according to the logs, but could it also work as "!me.hasAnnotations("DOM", "CON")
		if (!me.hasAnnotations("DOM+COO"))
			return;

		if (!me.hasAnnotations("COO+DOM"))
			return;
		
		// Add to rolling window
		RollingWindow.sharedWindow().addEvent(event, TRIGGER_NAME, "DOM_COO");
		
		// if DOM+CON has been identified more than 3 times in the last 5 minutes
		
		//returns a count of events occurring in the last secondsAgo seconds matching ALL keys
		if (RollingWindow.sharedWindow().countAnyEvents(HISTORY_WINDOW, "DOM", "COO") > 3)
		{
			// Then propose a cognitive trigger
			 Logger.commonLog(getClass().getSimpleName(), Logger.LOG_NORMAL,
                "TRIGGER FIRED: COGNITIVE");
            
            MessageEvent triggerMsg = new MessageEvent(source, "MAI_LISTENER", TRIGGER_NAME, "METACOGNITIVE_TRIGGER");
            source.addPreprocessedEvent(triggerMsg);
			
			
		}
	}

	
	/**
	 * @return the classes of events that this Preprocessor cares about
	 */
	@Override
	public Class[] getPreprocessorEventClasses()
	{
		//only MessageEvents will be delivered to this watcher.
		return new Class[]{MessageEvent.class};
	}

}
