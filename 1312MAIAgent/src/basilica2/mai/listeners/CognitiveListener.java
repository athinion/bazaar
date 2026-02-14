package basilica2.mai.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edu.cmu.cs.lti.basilica2.core.Event;
import basilica2.agents.components.InputCoordinator;
import basilica2.agents.events.MessageEvent;
import basilica2.agents.listeners.BasilicaPreProcessor;

import basilica2.agents.components.StateMemory;
import edu.cmu.cs.lti.basilica2.core.Agent;
import edu.cmu.cs.lti.project911.utils.log.Logger;

import basilica2.agents.data.RollingWindow


/**
 * Cognitive Actor for MAI F2F
 * 
 * Triggers when: (DOM is High) AND (CON is High) AND (FCD is Medium OR FCD is High)
 * Blackout: 180 seconds
 */
public class CognitiveListener implements BasilicaPreProcessor {


	//a toy-sized model of message history - see "RollingWindow" for a richer event log.
	//private ArrayList<String> messages = new ArrayList<String>();
	
	protected static final int HISTORY_WINDOW = 300; // define history window

	public CognitiveListener(Agent a) {
		super(a);
		promptLabel = "METACOGNITIVE";
		loadAvailablePrompts();
	}

	private void loadAvailablePrompts() {
		// All prompts come from intervention_prompts.xml with ID "METACOGNITIVE"
		// The PromptTable handles selecting random text variants
		availablePrompts.add("METACOGNITIVE");
	}
 
	 // Configure RollingWindow history size + purge interval
	RollingWindow.sharedWindow().setWindowSize(HISTORY_WINDOW, 2);

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
		MessageEvent me = (MessageEvent)event;
		//String normalizedText = me.getText().toLowerCase();
		
		// if the message doesn't have the annotations that CognitiveListener is looking for, return
		if !(me.hasAnnotations("DOM+CON" || "CON+DOM"))
			return;
		
		
		
		// start calculating FCD (Count of messages that are classified as both DOM and CON in the last 5 minutes)
		
		
		// if DOM+CON has been identified more than 3 times in the last 5 minutes, propose a cognitive trigger
		
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
