package basilica2.mai.listeners;

import edu.cmu.cs.lti.basilica2.core.Agent;
import edu.cmu.cs.lti.basilica2.core.Event;

import java.util.HashMap;
import java.util.Map;
import java.io.PrintWriter;
import java.io.IOException;
import java.net.Socket;

import org.json.JSONObject;
import org.json.JSONException;

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
	
	// Map trigger types to audio categories for client_mic.py
	private static final Map<String, String> TRIGGER_TO_MOVE = new HashMap<String, String>() {{
		put("METACOGNITIVE", "metacognitive");
		put("COGNITIVE", "cognitive");
		put("BEHAVIORAL", "behavioral");
		put("AFFECTIVE", "socio_emotional");
		put("SOCIAL", "shared_perspective");
	}};
	
	// Socket configuration for sending to pipeline
	private String socketHost = "localhost";
	private int socketPort = 9999;
	private Socket socket;
	private PrintWriter socketWriter;

	public MAIActor(Agent a) {
    	promptTable = new PromptTable("plans/intervention_prompts_en.xml");
    	for(String k : PRIORITY.keySet()) lastFireTime.put(k, 0L);
    	initializeSocket();
	}
	
	public MAIActor(Agent a, String n, String pf)
	{
	    this(a);
	}
	
	public MAIActor() {
	    promptTable = new PromptTable("plans/intervention_prompts_en.xml");
	    for(String k : PRIORITY.keySet()) lastFireTime.put(k, 0L);
	    initializeSocket();
	}
	
	@Override
	public void processEvent(InputCoordinator source, Event event)
	{
		if(!(event instanceof MessageEvent)) return;
    	MessageEvent me = (MessageEvent) event;

   	 // identify trigger type from annotations
    	String triggerType = null;
    	if(me.hasAnnotation("COGNITIVE_TRIGGER")) {
    		triggerType = "COGNITIVE";
    	} else if(me.hasAnnotation("METACOGNITIVE_TRIGGER")) {
    		triggerType = "METACOGNITIVE";
    	} else if(me.hasAnnotation("BEHAVIORAL_TRIGGER")) {
    		triggerType = "BEHAVIORAL";
    	} else if(me.hasAnnotation("AFFECTIVE_TRIGGER")) {
    		triggerType = "AFFECTIVE";
    	} else if(me.hasAnnotation("SOCIAL_TRIGGER")) {
    		triggerType = "SOCIAL";
    	}
    	
    	if(triggerType == null) return;
    	
    	// Check cooldown
    	long last = lastFireTime.getOrDefault(triggerType, 0L);
    	if(System.currentTimeMillis() - last < COOLDOWN_MS) return;

    	// Get prompt text
    	String promptText = promptTable.lookup(triggerType);
    	if(promptText == null || promptText.isEmpty()) return;

    	// Create PromptEvent and enqueue so PromptActor handles delivery
    	PromptEvent pe = new PromptEvent(source, promptText, me.getFrom());
    	source.addPreprocessedEvent(pe);

    	// Set cooldown
    	lastFireTime.put(triggerType, System.currentTimeMillis());
    	
    	// Send JSON message to pipeline with audio category
    	sendJsonToPipeline(triggerType, promptText, me.getText());
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
	
	/**
	 * Initialize socket connection to pipeline (client_mic.py)
	 */
	private void initializeSocket() {
		try {
			socket = new Socket(socketHost, socketPort);
			socketWriter = new PrintWriter(socket.getOutputStream(), true);
			System.out.println("[MAIActor] Connected to pipeline at " + socketHost + ":" + socketPort);
		} catch(IOException e) {
			System.out.println("[MAIActor] Warning: Could not connect to pipeline socket at " + socketHost + ":" + socketPort + " - " + e.getMessage());
			socket = null;
			socketWriter = null;
		}
	}
	
	/**
	 * Ensure socket is still connected, reconnect if needed
	 */
	private void ensureSocketConnected() {
		if(socket == null || socket.isClosed() || !socket.isConnected()) {
			System.out.println("[MAIActor] Socket disconnected, attempting to reconnect...");
			initializeSocket();
		}
	}
	
	/**
	 * Send JSON message to pipeline with trigger response
	 * Format: {"response": "...", "selected_move": "cognitive|metacognitive|behavioral|socio_emotional|shared_perspective", "transcription": "..."}
	 */
	private void sendJsonToPipeline(String triggerType, String response, String transcription) {
		if(socket == null || socketWriter == null) {
			System.out.println("[MAIActor] Socket not initialized, skipping send");
			return;
		}
		
		try {
			ensureSocketConnected();
			
			String audioMove = TRIGGER_TO_MOVE.get(triggerType);
			if(audioMove == null) {
				System.out.println("[MAIActor] No audio move mapping for trigger: " + triggerType);
				return;
			}
			
			// Create JSON message
			JSONObject jsonMessage = new JSONObject();
			jsonMessage.put("response", response);
			jsonMessage.put("selected_move", audioMove);
			jsonMessage.put("transcription", transcription);
			
			// Send through socket
			socketWriter.println(jsonMessage.toString());
			socketWriter.flush();
			
			System.out.println("[MAIActor] Sent to pipeline: " + jsonMessage.toString());
			
		} catch(JSONException e) {
			System.out.println("[MAIActor] JSON error: " + e.getMessage());
		} catch(IOException e) {
			System.out.println("[MAIActor] Socket error: " + e.getMessage());
			socket = null;
			socketWriter = null;
		}
	}

}
