package basilica2.mai.listeners;

import edu.cmu.cs.lti.basilica2.core.Agent;
import edu.cmu.cs.lti.basilica2.core.Event;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import basilica2.agents.components.InputCoordinator;
import basilica2.agents.components.OutputCoordinator;
import basilica2.agents.events.MessageEvent;
import basilica2.agents.events.priority.PriorityEvent;
import basilica2.agents.listeners.BasilicaListener;

public class MAIActor implements BasilicaListener
{

	// queue storing trigger events
	ConcurrentLinkedQueue<MessageEvent> q = new ConcurrentLinkedQueue<>();

	// last time each trigger fired
	private Map<String,Long> lastFireTime = new HashMap<>();

	private static final long COOLDOWN_MS = 180000; // 3 min
	private static final long DECISION_WAIT_MS = 2000; // wait before deciding
	private static final long TRIGGER_EXPIRE_MS = 5000; // discard old triggers

	private boolean timerRunning = false;

	private ScheduledExecutorService scheduler =
			Executors.newSingleThreadScheduledExecutor();

	// priority ranking
	private static final Map<String,Integer> PRIORITY = new HashMap<String,Integer>() {{
    	put("METACOGNITIVE",1);
		put("COGNITIVE",2);
		put("BEHAVIORAL",3);
   		put("AFFECTIVE",4);
		put("SOCIAL",5);
	}};

	// Map trigger types to audio categories
	private static final Map<String, String> TRIGGER_TO_MOVE = new HashMap<String, String>() {{
		put("METACOGNITIVE", "metacognitive");
		put("COGNITIVE", "cognitive");
		put("BEHAVIORAL", "behavioral");
		put("AFFECTIVE", "socio_emotional");
		put("SOCIAL", "shared_perspective");
	}};

	public MAIActor(Agent a) {
    	for(String k : PRIORITY.keySet())
			lastFireTime.put(k, 0L);
	}

	public MAIActor(Agent a, String n, String pf)
	{
	    this(a);
	}

	public MAIActor() {
	    for(String k : PRIORITY.keySet())
			lastFireTime.put(k, 0L);
	}

	@Override
	public void processEvent(InputCoordinator source, Event event)
	{
		if(!(event instanceof MessageEvent)) return;

    	MessageEvent me = (MessageEvent) event;
    	
    	for (int i=0; i<me.getAllAnnotations().length; i++) {
    		System.out.println("Extracting trigger type from message event: " + me.getAllAnnotations()[i]);
    	}

		String triggerType = me.getAllAnnotations()[0];

		// Ignore messages that are not valid triggers
		if(!PRIORITY.containsKey(triggerType))
			return;

    	System.out.println("MAIActor received trigger: " + triggerType);

    	// add event to queue
    	q.add(me);

    	// if this is the first event → start decision timer
    	if (!timerRunning) {

    		timerRunning = true;

    		scheduler.schedule(() -> decideTrigger(source),
    				DECISION_WAIT_MS,
    				TimeUnit.MILLISECONDS);
    	}
	}

	// Called when timer expires
	private void decideTrigger(InputCoordinator source)
	{
		long now = System.currentTimeMillis();

		MessageEvent best = null;
		int bestPriority = Integer.MAX_VALUE;

		for(MessageEvent e : q)
		{
			// discard expired triggers
			if(now - e.getTimestamp() > TRIGGER_EXPIRE_MS)
				continue;

			String triggerType = e.getText();

			int p = PRIORITY.getOrDefault(triggerType, Integer.MAX_VALUE);

			long lastFire = lastFireTime.getOrDefault(triggerType, 0L);

			// enforce cooldown
			if(now - lastFire < COOLDOWN_MS)
				continue;

			if(p < bestPriority)
			{
				bestPriority = p;
				best = e;
			}
		}

		if(best != null)
		{
			fireTrigger(source, best);
		}

		q.clear();
		timerRunning = false;
	}

	// Send chosen trigger forward
	private void fireTrigger(InputCoordinator source, MessageEvent e)
	{
		String triggerType = e.getText();

		System.out.println("MAIActor firing trigger: " + triggerType);

		lastFireTime.put(triggerType, System.currentTimeMillis());

		source.pushProposal(PriorityEvent.makeBlackoutEvent("macro", "MAITriggerEvent", e, OutputCoordinator.HIGH_PRIORITY, 5.0, 2));
	}


	@Override
	public Class[] getListenerEventClasses()
	{
		return new Class[]{MessageEvent.class};
	}

}