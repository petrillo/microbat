package microbat.model.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import microbat.model.AttributionVar;
import microbat.model.BreakPoint;
import microbat.model.Scope;
import microbat.model.UserInterestedVariables;
import microbat.model.value.VarValue;
import microbat.model.value.VirtualValue;
import microbat.model.variable.Variable;
import microbat.util.Settings;

/**
 * This class stands for a trace for an execution
 * @author Yun Lin
 *
 */
public class Trace {
	private int observingIndex = -1;
	
	/**
	 * This variable indicate the time of user ask for recommendation, in addition, the check time is also used
	 * to specify the time of a variable marked as "incorrect". Note that, newer variables has more importance
	 * in the trace.
	 */
	private int checkTime = 1;
	
	private List<TraceNode> exectionList = new ArrayList<>();
	/**
	 * tracking which steps read/write what variables, and what variables are read/written by which steps.
	 * key is the variable ID, and value is the entry containing all the steps reading/writing the corresponding
	 * variable.
	 */
	private Map<String, StepVariableRelationEntry> stepVariableTable = new HashMap<>();
	
	/**
	 * the time used to construct the trace, which is used for evaluation.
	 */
	private int constructTime = 0;

	public void resetCheckTime(){
		this.checkTime = -1;
		for(TraceNode node: getExectionList()){
			node.resetCheckTime();
		}
	}
	
	public List<TraceNode> getExectionList() {
		return exectionList;
	}

	public void setExectionList(List<TraceNode> exectionList) {
		this.exectionList = exectionList;
	}
	
	public void addTraceNode(TraceNode node){
		this.exectionList.add(node);
	}
	
	public int size(){
		return this.exectionList.size();
	}
	
	public List<TraceNode> getTopMethodLevelNodes(){
		List<TraceNode> topList = new ArrayList<>();
		for(TraceNode node: this.exectionList){
			if(node.getInvocationParent() == null){
				topList.add(node);
			}
		}
		
		return topList;
	}
	
	public List<TraceNode> getTopLoopLevelNodes(){
		List<TraceNode> topList = new ArrayList<>();
		for(TraceNode node: this.exectionList){
			if(node.getLoopParent() == null){
				topList.add(node);
			}
		}
		
		return topList;
	}
	
	public List<TraceNode> getTopAbstractionLevelNodes(){
		List<TraceNode> topList = new ArrayList<>();
		for(TraceNode node: this.exectionList){
			if(node.getAbstractionParent() == null){
				topList.add(node);
			}
		}
		
		return topList;
	}
	
	public TraceNode getLastestNode(){
		int len = size();
		if(len > 0){
			return this.exectionList.get(len-1);
		}
		else{
			return null;
		}
	}
	
	public void resetObservingIndex(){
		this.observingIndex = -1;
	}
	
	public int getObservingIndex() {
		return observingIndex;
	}

	public void setObservingIndex(int observingIndex) {
		this.observingIndex = observingIndex;
	}
	
	public int searchBackwardTraceNode(String expression){
		int resultIndex = -1;
		
		for(int i=observingIndex-1; i>=0; i--){
			TraceNode node = exectionList.get(i);
			BreakPoint breakPoint = node.getBreakPoint();
			String className = breakPoint.getDeclaringCompilationUnitName();
			int lineNumber = breakPoint.getLineNumber();
			
			String exp = combineTraceNodeExpression(className, lineNumber);
			if(exp.equals(expression)){
				resultIndex = i;
				break;
			}
		}
		
		if(resultIndex != -1){
			this.observingIndex = resultIndex;
		}
		return resultIndex;
	}

	public int searchForwardTraceNode(String expression){
		int resultIndex = -1;
		
		for(int i=observingIndex+1; i<exectionList.size(); i++){
			TraceNode node = exectionList.get(i);
			BreakPoint breakPoint = node.getBreakPoint();
			String className = breakPoint.getDeclaringCompilationUnitName();
			int lineNumber = breakPoint.getLineNumber();
			
			String exp = combineTraceNodeExpression(className, lineNumber);
			if(exp.equals(expression)){
				resultIndex = i;
				break;
			}
		}
		
		if(resultIndex != -1){
			this.observingIndex = resultIndex;			
		}
		return resultIndex;
	}
	
	public static String combineTraceNodeExpression(String className, int lineNumber){
		className = className.substring(className.lastIndexOf(".")+1, className.length());
		
		String exp = className + " line:" + lineNumber;
		return exp;
	}

	public void conductStateDiff() {
		for(int i=0; i<this.exectionList.size(); i++){
			TraceNode node = this.exectionList.get(i);
			node.conductStateDiff();
		}
		
	}
	
	public void constructLoopParentRelation(){
		Stack<TraceNode> loopParentStack = new Stack<>();
		
		for(TraceNode node: this.exectionList){
			
			if(node.getOrder() == 14){
				System.currentTimeMillis();
			}
			
			/**
			 * if out of the scope the loop parent, pop
			 * this step decide the influential loop parent.
			 */
			if(!loopParentStack.isEmpty()){
				TraceNode currentLoopParent = loopParentStack.peek();
				while(!isLoopParentContainDirectlyOrIndirectly(currentLoopParent, node)
						                                                                 /**for recursive case*/
						|| (node.getLineNumber() == currentLoopParent.getLineNumber() && loopParentHaveNotLoopChildOfSomeInvocationParentOfNode(currentLoopParent, node))
						|| (node.getOrder()==currentLoopParent.getOrder()+1 && !currentLoopParent.getLoopScope().containsNodeScope(node))){
					if(currentLoopParent.getOrder()==5){
						System.currentTimeMillis();
//						loopParentDoesNotContainSomeInvocationParentOfNode(currentLoopParent, node);
					}
					
					loopParentStack.pop();
					if(loopParentStack.isEmpty()){
						break;
					}
					currentLoopParent = loopParentStack.peek(); 
				}
			}
			
			/**
			 * connect loop parent-child relation
			 * this step decide the direct loop child for the influential loop parent in the peek of the stack
			 */
			if(!loopParentStack.isEmpty()){
				TraceNode loopParent = loopParentStack.peek();
				if(loopParent.getLoopScope().containsNodeScope(node) && loopParentHaveNotLoopChildOfSomeInvocationParentOfNode(loopParent, node)){
					loopParent.addLoopChild(node);
					node.setLoopParent(loopParent);					
				}
			}
			
			/**
			 * if a node is a loop condition, push
			 */
			if(node.isLoopCondition()){
				loopParentStack.push(node);
			}
		}
	}

	private boolean loopParentHaveNotLoopChildOfSomeInvocationParentOfNode(TraceNode currentLoopParent, TraceNode node) {
		List<TraceNode> invocationParents = node.findAllInvocationParents();
		for(TraceNode parent: invocationParents){
			if(currentLoopParent.getLoopChildren().contains(parent)){
				return false;
			}
		}
		
		return true;
	}

	private boolean isLoopParentContainDirectlyOrIndirectly(TraceNode currentLoopParent, TraceNode node) {
		List<TraceNode> invocationParentList = new ArrayList<>();
		TraceNode invocationParent = node;
		while(invocationParent != null){
			invocationParentList.add(invocationParent);
			invocationParent = invocationParent.getInvocationParent();
		}
		
		for(TraceNode iParent: invocationParentList){
			if(currentLoopParent.getLoopScope().containsNodeScope(iParent)){
				return true;
			}
		}
		
		return false;
	}

	public void constructDomianceRelation(){
		constructDataDomianceRelation();
//		constructControlDomianceRelation0();
		constructControlDomianceRelation();
	}
	
	private void constructControlDomianceRelation() {
		TraceNode controlDominator = null;
		for(TraceNode node: this.exectionList){
			
			if(controlDominator != null){
				if(controlDominator.getControlScope() == null){
					System.currentTimeMillis();
				}
				
				if(isContainedInScope(node, controlDominator.getControlScope())){
					controlDominator.addControlDominatee(node);
					node.setControlDominator(controlDominator);
				}
				/** which means the {@code controlDominator} is no longer effective now */
				else{
					controlDominator = findContainingControlDominator(node, controlDominator);
					if(controlDominator != null){
						controlDominator.addControlDominatee(node);
						node.setControlDominator(controlDominator);
					}
				}
			}
			
			if(node.isConditional()){
				controlDominator = node;
			}
		}
	}

	private TraceNode findContainingControlDominator(TraceNode node, TraceNode controlDominator) {
		TraceNode superControlDominator = controlDominator.getControlDominator();
		while(superControlDominator != null){
			if(isContainedInScope(node, superControlDominator.getControlScope())){
				return superControlDominator;
			}
			superControlDominator = superControlDominator.getControlDominator();
		}
		
		return null;
	}

	/**
	 * I will consider the invocation parents of {@code node} as well
	 * @param node
	 * @param conditionScope
	 * @return
	 */
	private boolean isContainedInScope(TraceNode node, Scope conditionScope) {
		List<TraceNode> testingSet = new ArrayList<>();
		testingSet.add(node);
		List<TraceNode> invocationParents = node.findAllInvocationParents();
		testingSet.addAll(invocationParents);
		
		for(TraceNode n: testingSet){
			if(conditionScope.containsNodeScope(n)){
				return true;
			}
		}
		
		return false;
	}

//	@Deprecated
//	private void constructControlDomianceRelation0() {
//		if(this.exectionList.size()>1){
//			for(int i=this.exectionList.size()-1; i>=1; i--){
//				TraceNode dominatee = this.exectionList.get(i);
//				List<TraceNode> controlDominators = findControlDominators(dominatee.getOrder());
//				
//				for(TraceNode controlDominator: controlDominators){
//					dominatee.addControlDominator(controlDominator);
//					controlDominator.addControlDominatee(dominatee);
//				}
//			}			
//		}
//		
//	}

//	private List<TraceNode> findControlDominators(int startOrder) {
//		
//		List<TraceNode> controlDominators = new ArrayList<>();
//
//		TraceNode dominatee = this.exectionList.get(startOrder-1);
//		for(int i=startOrder-1-1; i>=0; i--){
//			TraceNode node = this.exectionList.get(i);
//			
//			if(node.isConditional()){
//				Scope conditionScope = node.getConditionScope();
//				if(conditionScope != null){
//					if(conditionScope.containsNodeScope(dominatee)){
//						controlDominators.add(node);
//						return controlDominators;
//					}
//					else if(conditionScope.hasJumpStatement()){
//						controlDominators.add(node);
//					}
//				}
//				
//			}
//			
//			if(node.equals(dominatee.getInvocationParent())){
//				dominatee = dominatee.getInvocationParent();
//			}
//		}
//		
//		return controlDominators;
//	}

	private void constructDataDomianceRelation() {
		for(String varID: this.stepVariableTable.keySet()){
			
			if(varID.equals("8.flag:32")){
				System.currentTimeMillis();
			}
			
			StepVariableRelationEntry entry = this.stepVariableTable.get(varID);
			List<TraceNode> producers = entry.getProducers();
			List<TraceNode> consumers = entry.getConsumers();
			
			if(producers.isEmpty()){
				//System.err.println("there is no producer for variable " + entry.getAliasVariables());
			}
			
			if(producers.size() > 1){
				System.err.println("there are more than one producer for variable " + entry.getAliasVariables());
			}

			if(!producers.isEmpty()){
				TraceNode producer = producers.get(0);
				List<String> varList = new ArrayList<>();
				varList.add(varID);
				for(TraceNode consumer: consumers){
					producer.addDataDominatee(consumer, varList);
					consumer.addDataDominator(producer, varList);
				}
				
			}
			
			
			
//			Collections.sort(producers, new TraceNodeOrderComparator());
//			Collections.sort(consumers, new TraceNodeOrderComparator());
//			
//			
//			int readingCursor = 0;
//			System.currentTimeMillis();
//			
//			for(int i=0; i<producers.size(); i++){
//				TraceNode prevWritingNode = producers.get(i);
//				TraceNode postWritingNode = null; 
//				if(i+1 < producers.size()){
//					postWritingNode = producers.get(i+1);					
//				}
//				
//				if(readingCursor >= consumers.size()){
//					break;
//				}
//				TraceNode readingNode = consumers.get(readingCursor);
//				int readingOrder = readingNode.getOrder();
//				
//				while(readingOrder <= prevWritingNode.getOrder()){
//					System.out.println("WARNING in Trace.constructDominanceRelation(): the consumer's order appears "
//							+ "to be smaller than producer's order for variable " + entry.getVarID() + ": " + entry.getAliasVariables());
//					
//					readingCursor++;
//					if(readingCursor >= consumers.size()){
//						break;
//					}
//					readingNode = consumers.get(readingCursor);
//					readingOrder = readingNode.getOrder();
//				}
//				
//				
//				if(postWritingNode != null){
//					int preOrder = prevWritingNode.getOrder();
//					int postOrder = postWritingNode.getOrder();
//					
//					if(preOrder == 158){
//						System.currentTimeMillis();
//					}
//					
//					if(readingCursor<consumers.size()){
//						
//						
//						while(preOrder<readingOrder && readingOrder<=postOrder){
//							
//							List<String> varIDs = new ArrayList<>();
//							varIDs.add(varID);
//							
//							prevWritingNode.addDataDominatee(readingNode, varIDs);
//							readingNode.addDataDominator(prevWritingNode, varIDs);
//							
//							readingCursor++;
//							if(readingCursor >= consumers.size()){
//								break;
//							}
//							
//							readingNode = consumers.get(readingCursor);
//							readingOrder = readingNode.getOrder();
//						}
//					}
//					else{
//						break;
//					}
//				}
//				else{
//					while(readingCursor<consumers.size()){
//						List<String> varIDs = new ArrayList<>();
//						varIDs.add(varID);
//						
//						prevWritingNode.addDataDominatee(readingNode, varIDs);
//						readingNode.addDataDominator(prevWritingNode, varIDs);
//						
//						readingCursor++;
//						if(readingCursor >= consumers.size()){
//							break;
//						}
//						
//						readingNode = consumers.get(readingCursor);
//						readingOrder = readingNode.getOrder();
//					}
//				}
//			}
		}
		
	}

	public Map<String, StepVariableRelationEntry> getStepVariableTable() {
		return stepVariableTable;
	}

	public TraceNode findLastestExceptionNode() {
		for(int i=0; i<exectionList.size(); i++){
			TraceNode lastestNode = exectionList.get(exectionList.size()-1-i);
			if(lastestNode.isException()){
				return lastestNode;
			}
		}
		
		return null;
	}
	
	public String findDefiningNodeOrder(String accessType, TraceNode currentNode, String varID) {
		String definingOrder = "0";
		if(accessType.equals(Variable.WRITTEN)){
			definingOrder = String.valueOf(currentNode.getOrder());
		}
		else if(accessType.equals(Variable.READ)){
			TraceNode node = null;
			TraceNode stepOverPreviousNode = currentNode.getStepOverPrevious();
			if(stepOverPreviousNode != null){
				if(stepOverPreviousNode.getLineNumber() == currentNode.getLineNumber()){
					node = findLastestNodeDefiningPrimitiveVariable(varID, stepOverPreviousNode.getOrder());
				}
				else{
					node = findLastestNodeDefiningPrimitiveVariable(varID, currentNode.getOrder());
				}
			}
			else{
				node = findLastestNodeDefiningPrimitiveVariable(varID, currentNode.getOrder());
			}
			if(node != null){
				definingOrder = String.valueOf(node.getOrder());				
			}
		}
		
		return definingOrder;
	}

	public TraceNode findLastestNodeDefiningPrimitiveVariable(String varID, int limitOrder){
		for(int i=limitOrder-2; i>=0; i--){
			TraceNode node = exectionList.get(i);
			for(VarValue var: node.getWrittenVariables()){
				String writtenVarID = var.getVarID();
				if(writtenVarID.contains(":")){
					String simpleVarID = writtenVarID.substring(0, writtenVarID.indexOf(":"));
					if(simpleVarID.equals(varID)){
						return node;
					}
				}
			}
		}
		
		return null;
	}

	public TraceNode findLastestNodeDefiningPrimitiveVariable(String varID) {
		TraceNode node = findLastestNodeDefiningPrimitiveVariable(varID, exectionList.size());
		return node;
		
//		for(int i=exectionList.size()-2; i>=0; i--){
//			TraceNode node = exectionList.get(i);
//			for(VarValue var: node.getWrittenVariables()){
//				String writtenVarID = var.getVarID();
//				if(writtenVarID.contains(":")){
//					String simpleVarID = writtenVarID.substring(0, writtenVarID.indexOf(":"));
//					if(simpleVarID.equals(varID)){
//						return node;
//					}
//				}
//			}
//		}
//		
//		return null;
	}

	/**
	 * Note that, if a variable is a primitive type, I cannot retrieve its heap address, therefore, I use the static approach
	 * to uniquely identify a variable, i.e., variable ID. Please refer to {@link microbat.model.variable.Variable#varID} for details.
	 * <br>
	 * <br>
	 * However, in order to save the parsing efficiency, the ID of variables of primitive types does not have the suffix of ":order".
	 * That's why I need to do the mapping from state variables to read/written variables.
	 * 
	 * @param varID
	 * @param order
	 * @return
	 */
	public String findTrueIDFromStateVariable(String varID, int order) {
		for(int i=order; i>=1; i--){
			TraceNode node = this.exectionList.get(i-1);
			String trueID = findTrueID(node.getWrittenVariables(), varID); 
			
			if(trueID != null){
				return trueID;
			}
			else{
				if(i != order){
					trueID = findTrueID(node.getReadVariables(), varID);
					if(trueID != null){
						return trueID;
					}
				}					
			}
		}
		return null;
	}
	
	private String findTrueID(List<VarValue> readOrWriteVars, String varID){
		for(VarValue var: readOrWriteVars){
			if(!(var instanceof VirtualValue)){
				String ID = var.getVarID();
				String concanateID = ID.substring(0, ID.indexOf(":"));
				if(concanateID.equals(varID)){
					return ID;
				}				
			}
		}
		
		return null;
	}

	public int getCheckTime() {
		return checkTime;
	}

	public void setCheckTime(int checkTime) {
		this.checkTime = checkTime;
	}
	
	public void clearAllSuspiciousness(){
		for(TraceNode node: this.exectionList){
			node.getSuspicousScoreMap().clear();
		}
	}

//	public TraceNode findSuspiciousControlDominator(TraceNode buggyNode, String feedback) {
//		
//		List<TraceNode> dominators;
//		if(feedback.equals(UserFeedback.WRONG_PATH)){
//			dominators = buggyNode.getControlDominators();
//		}
//		else{
//			dominators = new ArrayList<>(buggyNode.getDataDominator().keySet());
//		}
//		
//		if(dominators.isEmpty()){
//			return buggyNode;
//		}
//		else{
//			for(TraceNode controlDominator: dominators){
//				if(!controlDominator.hasChecked()){
//					return controlDominator;
//				}
//			}
//			return dominators.get(0);
//		}
//	}
	
	
	public void distributeSuspiciousness(UserInterestedVariables interestedVariables) {
		clearAllSuspiciousness();
		
		for(AttributionVar var: interestedVariables.getRoots()){
			String varID = var.getVarID();
			double suspicousness = 1;
			StepVariableRelationEntry entry = this.stepVariableTable.get(varID);
			
			if(entry == null){
				System.err.println("Cannot find " + varID + " in step variable table");
			}
			
			if(!entry.getProducers().isEmpty()){
				TraceNode producer = entry.getProducers().get(0);
				int layer = 1;
				distributeSuspiciousness(var, producer, suspicousness, layer);
			}
		}
		
	}
	
	private void distributeSuspiciousness(AttributionVar var, TraceNode producer, double suspiciousness, int layer){
		if(!producer.hasChecked()){
			double producerScore = suspiciousness * Settings.remainingRate;
			producer.addSuspicousScore(var, producerScore);
			
			suspiciousness = suspiciousness - producerScore;
		}
		
		if(layer < Settings.distribtionLayer){
			List<TraceNode> nonCorrectDominators = producer.getUncheckedDataDominators();
			if(!nonCorrectDominators.isEmpty()){
				int n = nonCorrectDominators.size();
				double subScore = suspiciousness/n;
				for(TraceNode dominator: nonCorrectDominators){
					distributeSuspiciousness(var, dominator, subScore, layer+1);
				}					
			}
			else{
				producer.addSuspicousScore(var, suspiciousness);
			}
		}
		else{
			producer.addSuspicousScore(var, suspiciousness);
		}
		
	}

	public TraceNode findMostSupiciousNode(AttributionVar var) {
		TraceNode suspiciousNode = null;
		for(TraceNode node: this.exectionList){
			if(suspiciousNode == null){
				suspiciousNode = node;
			}
			else{
				Double score1 = node.getSuspicousScore(var);
				score1 = (score1 == null) ? 0 : score1;
				Double score2 = suspiciousNode.getSuspicousScore(var);
				score2 = (score2 == null) ? 0 : score2;
				if(score1 > score2){
					suspiciousNode = node;
				}
				
//				if(node.getOrder()==203 || node.getOrder()==194){
//					System.out.println(node.getOrder() + "(" + var.getVarID() + "):" + node.getSuspicousScore(var));
//				}
			}
		}
		
		return suspiciousNode;
	}

	public TraceNode findOldestConflictNode(int order) {
		TraceNode oldestNode = null;
		
		TraceNode node = this.exectionList.get(order-1);
		for(TraceNode dominator: node.getDataDominator().keySet()){
			if(oldestNode == null){
				oldestNode = dominator;
			}
			else{
				if(oldestNode.getCheckTime() > dominator.getCheckTime()){
					oldestNode = dominator;
				}
			}
		}
		
		return oldestNode;
	}

	public LoopSequence findLoopRangeOf(TraceNode currentNode) {
		
		TraceNode controlDominator = currentNode.findContainingLoopControlDominator();
		
		if(controlDominator != null){
			List<TraceNode> allControlDominatees = controlDominator.findAllControlDominatees();
			Collections.sort(allControlDominatees, new TraceNodeOrderComparator());
			
			List<TraceNode> range = extendLoopRange(allControlDominatees, controlDominator);
			Collections.sort(range, new TraceNodeOrderComparator());
			
			LoopSequence loopSequence = new LoopSequence(range.get(0).getOrder(), 
					range.get(range.size()-1).getOrder());
			
			return loopSequence;
		}
		
		return null;
	}

	/**
	 * extend from one single iteration to all the iterations of the loop.
	 * @param allControlDominatees
	 * @param controlLoopDominator
	 * @return
	 */
	private List<TraceNode> extendLoopRange(List<TraceNode> allControlDominatees, TraceNode controlLoopDominator) {

		List<TraceNode> range = new ArrayList<>();
		for(int i=controlLoopDominator.getOrder()-2; i>=0; i--){
			TraceNode node = this.exectionList.get(i);
			boolean isInSameLoop = isInSameLoop(node, controlLoopDominator);
			if(isInSameLoop){
				range.add(node);
			}
			else{
				break;
			}
		}
		
		TraceNode lastLoopNode = allControlDominatees.get(allControlDominatees.size()-1);
		for(int i=lastLoopNode.getOrder()-1+1; i<exectionList.size(); i++){
			TraceNode node = this.exectionList.get(i);
			boolean isInSameLoop = isInSameLoop(node, controlLoopDominator);
			if(isInSameLoop){
				range.add(node);
			}
			else{
				break;
			}
		}
		
		range.addAll(allControlDominatees);
		return range;
	}

	/**
	 * check by seeing whether the control loop dominator of <code>node</code> has the same location
	 * with the parameter <code>controlLoopDominator</code>
	 * @param node
	 * @param controlLoopDominator
	 * @return
	 */
	private boolean isInSameLoop(TraceNode node, TraceNode controlLoopDominator) {
		
		TraceNode testNode = node;
		while(testNode != null && !testNode.hasSameLocation(controlLoopDominator)){
			testNode = testNode.findContainingLoopControlDominator();
		}
		
		return testNode != null;
	}

	public TraceNode getEarliestNodeWithWrongVar() {
		for(TraceNode node: this.exectionList){
			if(node.getWittenVarCorrectness(Settings.interestedVariables, false)==TraceNode.WRITTEN_VARS_INCORRECT
					|| node.getReadVarCorrectness(Settings.interestedVariables, false)==TraceNode.READ_VARS_INCORRECT){
				return node;
			}
		}
		return null;
	}
	
	public TraceNode getLatestProducer(int startOrder, String simpleVarID){
		int latestOrder = -1;
		String latestVarID = null;
		for(String varID: this.stepVariableTable.keySet()){
			String orderString = varID.substring(varID.indexOf(":")+1);
			int order = Integer.valueOf(orderString);
			
			String simVarID = Variable.truncateSimpleID(varID);
			if(order > startOrder || !simpleVarID.equals(simVarID)){
				continue;
			}
			
			if(latestOrder == -1){
				latestOrder = order;
				latestVarID = varID;
			}
			else{
				if(latestOrder > order){
					latestOrder = order;
					latestVarID = varID;
				}
			}
		}
		
		if(latestVarID != null){
			return getProducer(latestVarID);			
		}
		return null;
	}

	public TraceNode getProducer(String varID) {
		StepVariableRelationEntry entry = this.stepVariableTable.get(varID);
		
		if(entry == null){
			System.err.println("the variable with ID " + varID + " is not explicitly read or written");
			return null;
		}
		
		if(!entry.getProducers().isEmpty()){
			return entry.getProducers().get(0);
		}
		
		return null;
	}

	public int getConstructTime() {
		return constructTime;
	}

	public void setConstructTime(int constructTime) {
		this.constructTime = constructTime;
	}

	public TraceNode getLatestWrongNode() {
		for(TraceNode node: this.exectionList){
			if(!node.isAllReadWrittenVarCorrect(false) || node.isWrongPathNode()){
				return node;
			}
		}
		return null;
	}

	
}
