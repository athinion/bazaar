package basilica2.mai.listeners;

import java.awt.Window;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import edu.cmu.cs.lti.basilica2.core.Event;
import basilica2.agents.components.InputCoordinator;
import basilica2.agents.components.OutputCoordinator;
import basilica2.agents.events.MAITriggerEvent;
import basilica2.agents.events.MessageEvent;
import basilica2.agents.events.priority.PriorityEvent;
import basilica2.agents.listeners.BasilicaPreProcessor;

import edu.cmu.cs.lti.basilica2.core.Agent;
import edu.cmu.cs.lti.project911.utils.log.Logger;


import basilica2.agents.data.RollingWindow;


public class CognitiveListener implements BasilicaPreProcessor {


	// Configure RollingWindow history size + purge interval

	protected static final int HISTORY_WINDOW = 300; // define history window
	
    //private static final int TRIGGER_THRESHOLD = 3;
    private static final String TRIGGER_NAME = "COGNITIVE";
  //  private static final double PRIORITY = 2.0; // Priority level

	public CognitiveListener(Agent a) {
		RollingWindow.sharedWindow().setWindowSize(HISTORY_WINDOW, 2);
		Logger.commonLog(TRIGGER_NAME, TRIGGER_NAME, "rolling window created");
	}
	

	/**
	 * Preprocess an incoming event, by modifying this event or creating a new event in response. 
	 * All original and new events will be passed by the InputCoordinator to the second-stage Reactors ("BasilicaListener" instances).
	 */
	@Override
	public void preProcessEvent(InputCoordinator source, Event event)
	{
		
		Logger.commonLog(TRIGGER_NAME, TRIGGER_NAME, "Pre-processing event");

		if (!(event instanceof MessageEvent)) {
            return;
        }
		MessageEvent me = (MessageEvent)event;
		
		
		// this was made according to the logs, but could it also work as "!me.hasAnnotations("DOM", "CON")
		if (!me.hasAnnotations("DOM") || (!me.hasAnnotations("CONN")))
			return;
		
		// Add to rolling window
		RollingWindow.sharedWindow().addEvent(me, "CONN+DOM");
		Logger.commonLog(getClass().getSimpleName(), Logger.LOG_NORMAL, "Cognitive Event added");
		Logger.commonLog(getClass().getSimpleName(),Logger.LOG_NORMAL,RollingWindow.sharedWindow().getEvents("CONN+DOM").toString());
		
		MAITriggerEvent MAITriggerEvent = new MAITriggerEvent(source, TRIGGER_NAME);
      	System.err.println("CognitiveListener, execute - MAITriggerEvent created");
      	Logger.commonLog(getClass().getSimpleName(),Logger.LOG_NORMAL,"CognitiveListener, execute - MAITriggerEvent created");
		source.pushProposal(PriorityEvent.makeBlackoutEvent("macro", "MAITriggerEvent", MAITriggerEvent, OutputCoordinator.HIGH_PRIORITY, 5.0, 2));

		// if DOM+CON has been identified more than 3 times in the last 5 minutes
		
		//returns a count of events occurring in the last secondsAgo seconds matching ALL keys
		if (RollingWindow.sharedWindow().countEvents(HISTORY_WINDOW, "CONN+DOM") >= 3)
		{
			// Then propose a cognitive trigger
			Logger.commonLog(getClass().getSimpleName(), Logger.LOG_NORMAL, "Trigger should fire here!!!");
			 Logger.commonLog(getClass().getSimpleName(), Logger.LOG_NORMAL,
                "TRIGGER FIRED: COGNITIVE");
            
            MessageEvent triggerMsg = new MessageEvent(source, "MAI_LISTENER", TRIGGER_NAME, "COGNITIVE_TRIGGER");
            
			triggerMsg.addAnnotation("COGNITIVE_TRIGGER", Arrays.asList("COGNITIVE"));
			
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
