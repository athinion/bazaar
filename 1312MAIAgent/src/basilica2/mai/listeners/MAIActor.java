package basilica2.mai.listeners;

import edu.cmu.cs.lti.basilica2.core.Agent;
import edu.cmu.cs.lti.basilica2.core.Event;

import java.util.HashMap;
import java.util.Map;

import basilica2.agents.components.InputCoordinator;
import basilica2.agents.data.PromptTable;
import basilica2.agents.events.MessageEvent;
import basilica2.agents.events.PromptEvent;
import basilica2.agents.listeners.BasilicaListener;


public class MAIActor implements BasilicaListener
{

	// Received MAITriggerEvents from all MAI listeners
	// Prioritizes them according to priority ranking, forces cooldown (=3mins) and sends intervention prompts to the output coordinator
	// The priority order is (from highest to lowest): METACOGNITIVE, COGNITIVE, BEHAVIORAL, SOCIOEMOTIONAL, SHARED_PERSPECTIVE
	
	private PromptTable promptTable;
	private Map<String,Long> lastFireTime = new HashMap<>();
	private static final long COOLDOWN_MS = 180000; // 3 min
	private static final Map<String,Integer> PRIORITY = new HashMap<String,Integer>() {{
    	put("METACOGNITIVE",1); put("COGNITIVE",2); put("BEHAVIORAL",3);
   		put("AFFECTIVE",4); put("SOCIAL",5);
	}};

	public MAIActor(Agent a) {
    	promptTable = new PromptTable("runtime/plans/intervention_prompts_en.xml");
    	for(String k : PRIORITY.keySet()) lastFireTime.put(k, 0L);
	}

	
	@Override
	public void processEvent(InputCoordinator source, Event event)
	{
		if(!(event instanceof MessageEvent)) return;
    	MessageEvent me = (MessageEvent) event;

   	 // identify trigger (either via annotation or message text)
    	if(!me.hasAnyAnnotations("COGNITIVE_TRIGGER","METACOGNITIVE_TRIGGER", "BEHAVIORAL_TRIGGER", "AFFECTIVE_TRIGGER", "SOCIAL_TRIGGER"))
        	return;

   		String triggerName = me.getText(); // or derive from annotation
    	long last = lastFireTime.getOrDefault(triggerName, 0L);
    	if(System.currentTimeMillis() - last < COOLDOWN_MS) return;

    	String promptText = promptTable.lookup(triggerName);
    	if(promptText == null || promptText.isEmpty()) return;

    // create PromptEvent and enqueue so PromptActor handles delivery
    	PromptEvent pe = new PromptEvent(source, promptText, me.getFrom());
    	source.addPreprocessedEvent(pe);

    	lastFireTime.put(triggerName, System.currentTimeMillis());
	}


	/**
	 * @return the classes of events this reactor will respond to
	 */
	@Override
	public Class[] getListenerEventClasses()
	{
		//both RepeatEvents and MessageEvents will be forwarded to this reactor. 
		return new Class[]{MessageEvent.class};
	}

}
