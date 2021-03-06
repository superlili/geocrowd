/**
 * *****************************************************************************
 * @ Year 2013 This is the source code of the following papers.
 *
 * 1) Geocrowd: A Server-Assigned Crowdsourcing Framework. Hien To, Leyla
 * Kazemi, Cyrus Shahabi.
 *
 *
 * Please contact the author Hien To, ubriela@gmail.com if you have any
 * question.
 *
 * Contributors: Hien To - initial implementation
 * *****************************************************************************
 */
package org.geocrowd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Random;

import org.geocrowd.common.crowd.GenericTask;
import org.geocrowd.common.crowd.GenericWorker;
import org.geocrowd.common.crowd.SensingTask;
import org.geocrowd.common.crowd.VirtualWorker;
import org.geocrowd.common.entropy.EntropyRecord;
import org.geocrowd.common.utils.Utils;
import org.geocrowd.setcover.MultiSetCoverGreedy_CloseToDeadline;
import org.geocrowd.setcover.MultiSetCoverGreedy_LargeWorkerFanout;
import org.geocrowd.setcover.SetCoverGreedy;
import org.geocrowd.setcover.MultiSetCoverGreedy_HighTaskCoverage;
import org.geocrowd.setcover.SetCoverGreedy_CloseToDeadline;
import org.geocrowd.setcover.SetCoverGreedy_HighTaskCoverage;
import org.geocrowd.setcover.SetCoverGreedy_LargeWorkerFanout;
import org.geocrowd.setcover.SetCoverGreedy_LowWorkerCoverage;
import org.paukov.combinatorics.Factory;
import org.paukov.combinatorics.Generator;
import org.paukov.combinatorics.ICombinatoricsVector;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import com.google.common.hash.PrimitiveSink;

import org.geocrowd.datasets.params.GeocrowdConstants;
import org.geocrowd.datasets.params.GeocrowdSensingConstants;
import org.geocrowd.datasets.synthetic.Parser;

// TODO: Auto-generated Javadoc
/**
 * The Class Crowdsensing. Used to find the minimum number of workers that cover
 * maximum number of tasks. First, the class fetches workers and tasks by
 * readTasks and readWorkers. These functions fetch worker and task information
 * into workerList and taskList. Then, function matchingTasksWorkers is
 * executed, which compute task set covered by any worker (i.e., container).
 * Note that this function also compute the invertedContainer.
 *
 * The main function minimizeWorkersMaximumTaskCoverage compute maximum
 *
 */
public class GeocrowdSensing extends Geocrowd {

	/**
	 * Ranked by the size of worker set
	 */
	PriorityQueue<VirtualWorker> vWorkerList;

	/**
	 * store all virtual workers to easily get k-th element. similar role to
	 * workerList for redundant task assignment
	 */
	VirtualWorker[] vWorkerArray;

	/**
	 * Gets an array of workers, each worker is associated with a hashmap
	 * <taskid, deadline>.
	 *
	 * The order of the workers in is the same as in containerWorker
	 *
	 * @return a container with task deadline
	 */
	public ArrayList<HashMap<Integer, Integer>> getContainerWithDeadline() {
		ArrayList<HashMap<Integer, Integer>> containerWithDeadline = new ArrayList<>();
		//System.out.println("Container worker size = " + containerWorker.size());
		Iterator it = containerWorker.iterator();
		while (it.hasNext()) {
			ArrayList taskids = (ArrayList) it.next();
			Iterator it2 = taskids.iterator();
			HashMap<Integer, Integer> taskidsWithDeadline = new HashMap();
			while (it2.hasNext()) {
				Integer taskid = (Integer) it2.next();
				taskidsWithDeadline.put(taskid,
						taskList.get(candidateTaskIndices.indexOf(taskid)) //?
								.getArrivalTime() + GeocrowdConstants.MAX_TASK_DURATION);
			}
			
			
			containerWithDeadline.add(taskidsWithDeadline);
		}
		return containerWithDeadline;
	}
	
	
	public  void matchingTaskWorkers2(){
		//build tasksMap and containerWorkerWithTaskDealine 
		tasksMap.clear();
		for(int i = 0;  i < taskList.size(); i++){
			if(!assignedTasks.contains((int) taskList.get(i).getId()))
				tasksMap.put((int) taskList.get(i).getId(), taskList.get(i));
		}
		containerWorkerWithTaskDeadline = new ArrayList<>();
		invertedContainer = new HashMap<>();
		
		for(int i = 0; i < workerList.size(); i++){
			
			HashMap<Integer, Integer> tasks_deadlines = new HashMap<>();
			for(Integer j: tasksMap.keySet()){
				GenericWorker w = workerList.get(i);
				SensingTask task = (SensingTask) tasksMap.get(j);
				if (GeocrowdTaskUtility.distanceWorkerTask(DATA_SET, w, task) <= GeocrowdSensingConstants.TASK_RADIUS
						&&  w.getOnlineTime() < task.getArrivalTime()+ task.lifetime
						&& w.getOnlineTime() >= task.getArrivalTime()
						){
					
					tasks_deadlines.put((int) task.getId(), task.getArrivalTime()+task.lifetime);
					if(invertedContainer.containsKey((int) task.getId())){
						invertedContainer.get((int) task.getId()).add(i);
					}
					else{
						ArrayList<Integer> workerIndices = new ArrayList<>();
						workerIndices.add(i);
						invertedContainer.put((int) task.getId(), workerIndices);
					}
				}
			}
			containerWorkerWithTaskDeadline.add(tasks_deadlines);
		}
		
		
	}

	/**
	 * Compute which worker within which task region and vice versa. Also remove
	 * workers with no tasks
	 */
	@Override
	public void matchingTasksWorkers() {
		invertedContainer = new HashMap<Integer, ArrayList<Integer>>();
		candidateTaskIndices = new ArrayList();
		taskSet = new HashSet<Integer>();
		containerWorker = new ArrayList<>();
		
//		for(int taskIdx = taskList.size() -1; taskIdx >=0; taskIdx -- ){
//			if(taskList.get(taskIdx).getArrivalTime() + 
//					GeocrowdSensingConstants.MAX_TASK_DURATION <= OnlineMTC.TimeInstance){
//				taskList.remove(taskIdx);
//			}
//		}
		
		
		containerPrune = new ArrayList[workerList.size()];

		
		// remove expired task from task list
		pruneExpiredTasks();

		for (int workeridx = 0; workeridx < workerList.size(); workeridx++) {
			reverseRangeQuery(workeridx);
		}

		
		// remove workers with no tasks
		for (int i = containerPrune.length - 1; i >= 0; i--) {
			if (containerPrune[i] == null || containerPrune[i].size() == 0) {
				workerList.remove(i);
			}
		}
		for (int i = 0; i < containerPrune.length; i++) {
			if (containerPrune[i] != null && containerPrune[i].size() > 0) {
				containerWorker.add(containerPrune[i]);
			}
		}
		/**
		 * update invertedContainer <taskid, ArrayList<workerIndex>>
		 */
		for (int tid = 0; tid < taskList.size(); tid++) {
			for (int i = 0; i < containerWorker.size(); i++) {
				final int workerIndex = i;
				if (containerWorker.get(workerIndex).contains(tid)) {
					if (!invertedContainer.containsKey(tid)) {
						invertedContainer.put(tid, new ArrayList() {
							{
								add(workerIndex);
							}
						});
					} else {
						invertedContainer.get(tid).add(workerIndex);
					}
				}
			}
		}

	}

	final Comparator<GenericTask> TASK_ORDER = new Comparator<GenericTask>() {
		public int compare(GenericTask t1, GenericTask t2) {
			if (t1.getRequirement() > t2.getRequirement()) {
				return -1;
			} else if (t1.getRequirement() < t2.getRequirement()) {
				return 1;
			} else {
				int idx1 = taskList.indexOf(t1);
				int idx2 = taskList.indexOf(t2);
				HashSet<Integer> workerIdxs1 = null;
				if (invertedContainer.containsKey(idx1)) {
					workerIdxs1 = new HashSet<Integer>(
							invertedContainer.get(idx1));
				}
				HashSet<Integer> workerIdxs2 = null;
				if (invertedContainer.containsKey(idx2)) {
					workerIdxs2 = new HashSet<Integer>(
							invertedContainer.get(idx2));
				}

				if (workerIdxs1 != null) {
					if (workerIdxs2 != null) {
						if (workerIdxs1.size() > workerIdxs2.size()) {
							return -1;
						} else if (workerIdxs1.size() < workerIdxs2.size()) {
							return 1;
						} else {
							return 0;
						}
					} else {
						return -1;
					}
				} else {
					if (workerIdxs2 != null) {
						return 1;
					} else {
						return 0;
					}
				}
			}
		}
	};

	Funnel<VirtualWorker> vworkerFunnel = new Funnel<VirtualWorker>() {
		@Override
		public void funnel(VirtualWorker w, PrimitiveSink into) {
			// into.putInt(w.getWorkerIds().hashCode());
			for (Integer i : w.getWorkerIds()) {
				into.putInt(i);
			}
		}
	};

	/**
	 * Virtual workers includes a set of worker ids that cover the same task.
	 *
	 * This function is only used for the case of redundant task assignment.
	 *
	 * Input: bipartite graph, in which each task has a parameter K - the number
	 * of of needed task responses.
	 *
	 * Output: container of virtual workers, similar to containerWorker so that
	 * this function is plug-able
	 */
	@SuppressWarnings("unchecked")
	public void populateVitualWorkers() {

		/**
		 * sort tasks by k in descending order *
		 */
		ArrayList<GenericTask> sortedTaskList = (ArrayList<GenericTask>) taskList
				.clone();
		Collections.sort(sortedTaskList, TASK_ORDER);

		HashMap<GenericTask, Integer> mapTaskIndices = new HashMap<GenericTask, Integer>();
		for (GenericTask t : sortedTaskList) {
			mapTaskIndices.put(t, taskList.indexOf(t));
		}

		/**
		 * create virtual worker, using priority queue
		 */
		BloomFilter<VirtualWorker> bf = BloomFilter.create(vworkerFunnel,
				1000000, 0.01);
		vWorkerList = new PriorityQueue<>();

		HashMap<Integer, ArrayList<SensingTask>> traversedTasks = new HashMap<Integer, ArrayList<SensingTask>>();
		int i = 0;
		for (final GenericTask t : sortedTaskList) {
			// get workers cover task
			int idx = mapTaskIndices.get(t);
			System.out.println("#task = " + i++ + " #k=" + t.getRequirement()
					+ " $vworkers = " + vWorkerList.size());

			ArrayList<Integer> workerIdxs = null;
			if (invertedContainer.containsKey(idx)) {
				workerIdxs = invertedContainer.get(idx);
			}
			/* remove tasks that are not covered by any worker */
			if (workerIdxs == null) {
				continue;
			}
			/**
			 * remove tasks that are covered by less than k workers
			 */
			if (t.getRequirement() > workerIdxs.size()) {
				continue;
			}

			/**
			 * Check condition 1: if the worker set of a task of k responses is
			 * covered by the worker set of another traversed task of the same k
			 */
			boolean isContinue = false;
			if (traversedTasks.containsKey(t.getRequirement())) {
				for (SensingTask st : traversedTasks.get(t.getRequirement())) {
					int idxj = mapTaskIndices.get(st);
					ArrayList<Integer> workerIdxsj = null;
					if (invertedContainer.containsKey(idxj)) {
						workerIdxsj = invertedContainer.get(idxj);
					}

					if (workerIdxsj != null
							&& workerIdxsj.containsAll(workerIdxs)) {
						isContinue = true;
						break;
					}
				}

				traversedTasks.get(t.getRequirement()).add((SensingTask) t);
			} else {
				traversedTasks.put(t.getRequirement(), new ArrayList<SensingTask>() {
					{
						add((SensingTask) t);
					}
				});
			}

			if (isContinue) {
				continue;
			}

			/**
			 * Check condition 2 (not often happen): if the worker set is
			 * covered by an existing logical worker --> do not need to worry
			 * about
			 */
			ICombinatoricsVector<Integer> initialVector = Factory
					.createVector(workerIdxs);

			/**
			 * Create a multi-combination generator to generate 3-combinations
			 * of the initial vector
			 */
			Generator<Integer> gen = Factory.createSimpleCombinationGenerator(
					initialVector, t.getRequirement());

			long start = System.nanoTime();

			/**
			 * Do not need to check if the first set
			 */
			if (vWorkerList.size() == 0) {
				for (ICombinatoricsVector<Integer> r : gen) {
					vWorkerList.add(new VirtualWorker(r.getVector()));
					bf.put(new VirtualWorker(r.getVector()));
				}
				continue;
			}

			start = System.nanoTime();
			for (ICombinatoricsVector<Integer> r : gen) {
				// check exist or covered by existing virtual worker
				VirtualWorker v = new VirtualWorker(r.getVector());
				if (bf.mightContain(v)) {
					if (!vWorkerList.contains(v)) {
						vWorkerList.add(v);
						bf.put(v);
					}
				} else {
					/**
					 * 100%
					 */
					vWorkerList.add(v);
					bf.put(v);
				}
			}
			long period = System.nanoTime() - start;
			System.out.println("      #workers = " + workerIdxs.size()
					+ " time (ms) = " + period / 1000000.0);
		}

		/**
		 * update connection between virtual worker and task.
		 */
		vWorkerArray = vWorkerList.toArray(new VirtualWorker[0]); // copy all
		// elements
		ArrayList containerVirtualWorker = new ArrayList<>();

		/**
		 * Iterate all worker virtual o
		 */
		for (int o = 0; o < vWorkerArray.length; o++) {
			VirtualWorker vw = vWorkerArray[o];

			/**
			 * <taskid,deadline>
			 */
			HashMap<Integer, Integer> taskids = new HashMap<>();
			/**
			 * Iterate all worker ids of virtual worker o
			 */
			ArrayList<HashMap<Integer, Integer>> cwWithTaskDeadline = getContainerWithDeadline();
			for (Integer j : vw.getWorkerIds()) {
				HashMap<Integer, Integer> tasksWithDeadlines = cwWithTaskDeadline
						.get(j);
				for (Integer t : tasksWithDeadlines.keySet()) {
					int k = taskList.get(candidateTaskIndices.get(t)).getRequirement();
					/**
					 * Check if the virtual worker is qualified for this task
					 */
					if (vw.getWorkerIds().size() >= k
							&& !taskids.containsKey(t)) {
						taskids.put(t, tasksWithDeadlines.get(t));
					}
				}

			}

			containerVirtualWorker.add(taskids);
		}

		containerWorker = containerVirtualWorker;
	}

	/**
	 * Select minimum number of workers that cover maximum number of tasks.
	 * After finding minimum number of workers that covers all tasks, remove all
	 * the assigned tasks from task list!
	 */
	public void minimizeWorkersMaximumTaskCoverage() {

		SetCoverGreedy sc = null;
		HashSet<Integer> minAssignedWorkers;

		switch (algorithm) {

		case GREEDY_HIGH_TASK_COVERAGE:

			// check using virtual worker or not
			if (vWorkerArray != null && vWorkerArray.length > 0) {
				/**
				 * containerWorker has been updated from function
				 * populateVirtualWorker
				 */
				sc = new SetCoverGreedy_HighTaskCoverage(containerWorker,
						TimeInstance);
				minAssignedWorkers = sc.minSetCover();

				HashSet<Integer> assignedWorkerList = new HashSet<>();
				for (Integer i : minAssignedWorkers) {
					// add all worker ID list of a virtual worker
					assignedWorkerList.addAll(vWorkerArray[i].getWorkerIds());
				}
				TotalAssignedWorkers += assignedWorkerList.size();
			} else {
				sc = new SetCoverGreedy_HighTaskCoverage(
						getContainerWithDeadline(), TimeInstance);
				minAssignedWorkers = sc.minSetCover();

				TotalAssignedWorkers += minAssignedWorkers.size();

			}
			TotalAssignedTasks += sc.assignedTasks;
			/**
			 * Why this?
			 */
			if (sc.averageDelayTime > 0) {
				AverageTimeToAssignTask += sc.averageDelayTime;
				numTimeInstanceTaskAssign += 1;
				System.out.println("average time: " + sc.averageDelayTime);

			}

			break;
		case GREEDY_HIGH_TASK_COVERAGE_MULTI:
			sc = new MultiSetCoverGreedy_HighTaskCoverage(
					getContainerWithDeadline(), TimeInstance);
			minAssignedWorkers = sc.minSetCover();

			TotalAssignedWorkers += minAssignedWorkers.size();
			TotalAssignedTasks += sc.assignedTasks;
			if (sc.averageDelayTime > 0) {
				AverageTimeToAssignTask += sc.averageDelayTime;
				numTimeInstanceTaskAssign += 1;
				System.out.println("average time: " + sc.averageDelayTime);
			}
			break;
		case GREEDY_LOW_WORKER_COVERAGE:
			sc = new SetCoverGreedy_LowWorkerCoverage(
					getContainerWithDeadline(), TimeInstance);
			minAssignedWorkers = sc.minSetCover();
			// if using virtual workers, compute real assigned workers
			if (vWorkerArray != null && vWorkerArray.length > 0) {
				HashSet<Integer> assignedWorkerList = new HashSet<>();
				for (Integer i : minAssignedWorkers) {
					// add all worker ID list of a virtual worker
					assignedWorkerList.addAll(vWorkerArray[i].getWorkerIds());
				}
				TotalAssignedWorkers += assignedWorkerList.size();
			} else {
				TotalAssignedWorkers += minAssignedWorkers.size();
			}
			TotalAssignedTasks += sc.universe.size();
			if (sc.averageDelayTime > 0) {
				AverageTimeToAssignTask += sc.averageDelayTime;
				numTimeInstanceTaskAssign += 1;
				System.out.println("average time: " + sc.averageDelayTime);
			}
			break;
		case GREEDY_LARGE_WORKER_FANOUT:

			/**
			 * check using virtual worker or not
			 */
			if (vWorkerArray != null && vWorkerArray.length > 0) {
				/**
				 * containerWorker has been updated from function
				 * populateVirtualWorker
				 */
				sc = new SetCoverGreedy_LargeWorkerFanout(containerWorker,
						TimeInstance);
				minAssignedWorkers = sc.minSetCover();

				HashSet<Integer> assignedWorkerList = new HashSet<>();
				for (Integer i : minAssignedWorkers) {
					// add all worker ID list of a virtual worker
					assignedWorkerList.addAll(vWorkerArray[i].getWorkerIds());
				}
				TotalAssignedWorkers += assignedWorkerList.size();
			} else {
				sc = new SetCoverGreedy_LargeWorkerFanout(
						getContainerWithDeadline(), TimeInstance);
				minAssignedWorkers = sc.minSetCover();

				TotalAssignedWorkers += minAssignedWorkers.size();

			}
			TotalAssignedTasks += sc.assignedTasks;
			if (sc.averageDelayTime > 0) {
				AverageTimeToAssignTask += sc.averageDelayTime;
				numTimeInstanceTaskAssign += 1;
				System.out.println("average time: " + sc.averageDelayTime);
			}
			break;
		case GREEDY_LARGE_WORKER_FANOUT_MULTI:
			sc = new MultiSetCoverGreedy_LargeWorkerFanout(
					getContainerWithDeadline(), TimeInstance);
			minAssignedWorkers = sc.minSetCover();

			TotalAssignedWorkers += minAssignedWorkers.size();
			TotalAssignedTasks += sc.assignedTasks;
			if (sc.averageDelayTime > 0) {
				AverageTimeToAssignTask += sc.averageDelayTime;
				numTimeInstanceTaskAssign += 1;
				System.out.println("average time: " + sc.averageDelayTime);
			}
			break;
		case GREEDY_CLOSE_TO_DEADLINE:

			if (vWorkerArray != null && vWorkerArray.length > 0) {
				/**
				 * containerWorker has been updated from function
				 * populateVirtualWorker
				 */
				sc = new SetCoverGreedy_CloseToDeadline(containerWorker,
						TimeInstance);
				minAssignedWorkers = sc.minSetCover();

				HashSet<Integer> assignedWorkerList = new HashSet<>();
				for (Integer i : minAssignedWorkers) {
					// add all worker ID list of a virtual worker
					assignedWorkerList.addAll(vWorkerArray[i].getWorkerIds());
				}
				TotalAssignedWorkers += assignedWorkerList.size();
			} else {
				HashMap<GenericTask, Double> entropies = new HashMap<GenericTask, Double>();
				for (GenericTask t : taskList) {
					entropies.put(t, computeCost(t));
				}
				sc = new SetCoverGreedy_CloseToDeadline(
						getContainerWithDeadline(), TimeInstance);
				((SetCoverGreedy_CloseToDeadline) sc).setEntropies(entropies);
				((SetCoverGreedy_CloseToDeadline) sc).setTaskList(taskList);
				minAssignedWorkers = sc.minSetCover();

				TotalAssignedWorkers += minAssignedWorkers.size();
			}
			TotalAssignedTasks += sc.assignedTasks;
			if (sc.averageDelayTime > 0) {
				AverageTimeToAssignTask += sc.averageDelayTime;
				numTimeInstanceTaskAssign += 1;
				System.out.println("average time: " + sc.averageDelayTime);
			}
			break;
		case GREEDY_CLOSE_TO_DEADLINE_MULTI:
			sc = new MultiSetCoverGreedy_CloseToDeadline(
					getContainerWithDeadline(), TimeInstance);
			minAssignedWorkers = sc.minSetCover();

			TotalAssignedWorkers += minAssignedWorkers.size();
			TotalAssignedTasks += sc.assignedTasks;
			if (sc.averageDelayTime > 0) {
				AverageTimeToAssignTask += sc.averageDelayTime;
				numTimeInstanceTaskAssign += 1;
				System.out.println("average time: " + sc.averageDelayTime);
			}
			break;
		}

		/**
		 * As all the tasks in the container are assigned, we need to remove
		 * them from task list.
		 */
		ArrayList<Integer> assignedTasks = new ArrayList<Integer>();
		// Iterator it = sc.universe.iterator();
		Iterator it = sc.assignedTaskSet.iterator();
		while (it.hasNext()) {
			Integer candidateIndex = (Integer) it.next();
			assignedTasks.add(candidateTaskIndices.get(candidateIndex));
		}

		/**
		 * sorting is necessary to make sure that we don't mess things up when
		 * removing elements from a list
		 */
		Collections.sort(assignedTasks);
		for (int i = assignedTasks.size() - 1; i >= 0; i--) {
			/* remove the last elements first */
			taskList.remove((int) assignedTasks.get(i));
		}
	}

	/**
	 * Read tasks from file.
	 *
	 * @param fileName
	 *            the file name
	 */
	@Override
	public void readTasks(String fileName) {
		TaskCount += Parser.parseSensingTasks(fileName, taskList);
	}
	
	/**
	 * workload
	 */
	public void readWorkloadTasks(String fileName, int startTime) {
		TaskCount += Parser.parseSensingTasks(fileName, startTime, taskList);
	}

	
	public static ArrayList<GenericTask> readTasks(String filename, int startTime){
		return Parser.parseSensingTasks2(filename, startTime);
	}
	/**
	 * Read workers from file Working region of each worker is computed from his
	 * past history.
	 *
	 * @param fileName
	 *            the file name
	 */
	@Override
	public void readWorkers(String fileName) {
		WorkerCount += Parser.parseGenericWorkers(fileName, workerList);
	}

	public ArrayList<GenericWorker> readWorkersWithLimit(String fileName, int entryTime, int limit) {
		ArrayList<GenericWorker> listWorkers = new ArrayList<>();
        Parser.parseSensingWorkers(fileName, listWorkers, entryTime);
      //  workerList.add(workerSampling(listWorkers, limit));
//        ArrayList<GenericWorker> result = workerSampling(listWorkers, limit);
        ArrayList<GenericWorker> result;
        
                if(limit < listWorkers.size())
                	result = new ArrayList( listWorkers.subList(0, limit));
                else result = listWorkers;
        
        workerList.addAll(result);
//       
        WorkerCount += result.size();
        return result;
        
    }
	private ArrayList<GenericWorker> workerSampling(ArrayList<GenericWorker> workerList, int limit) {
		ArrayList<GenericWorker> result = new ArrayList<>();
		double totalEntropy = 0;
		Geocrowd.DATA_SET = DatasetEnum.GOWALLA;
		GeocrowdInstance geoCrowd = new GeocrowdInstance();
		geoCrowd.printBoundaries();
		geoCrowd.createGrid();
		geoCrowd.readEntropy();
		for(EntropyRecord er: geoCrowd.entropyList){
			totalEntropy += er.getEntropy();
		}
		//System.out.println("Total entropy = "+ totalEntropy);
		Random r = new Random();
		
		while(result.size() < limit){
			double randomNumber = r.nextDouble()* totalEntropy;
			//System.out.println("Random number = "+ randomNumber);
			for(int i = 0; i < geoCrowd.entropyList.size(); i++){
				randomNumber -=geoCrowd.entropyList.get(i).getEntropy();
				if(randomNumber <=0){
					
					int row = geoCrowd.entropyList.get(i).getCoord().getRowId();
					int col = geoCrowd.entropyList.get(i).getCoord().getColId();
					double startLat = geoCrowd.rowToLat(row);
					double endLat = geoCrowd.rowToLat(row + 1);
					double startLon = geoCrowd.colToLng(col);
					double endLon = geoCrowd.colToLng(col+1);
					
					ArrayList<GenericWorker> workerInCells = getWorkerInCell(startLat, endLat, startLon, endLon, workerList);
				//	System.out.println("#workers in cells :"+ workerInCells.size() );
					if(workerInCells.size() >0)
					{
//						System.out.println("here");
						GenericWorker w = workerInCells.get((new Random()).nextInt( workerInCells.size()));
						result.add(w);
						break;
					}
					
				}
				if(result.size() > limit) break;
			}
		}
		return result;
	}


	private ArrayList<GenericWorker> getWorkerInCell(double startLat, double endLat, double startLon, double endLon, ArrayList<GenericWorker> workerList) {
		ArrayList<GenericWorker> result = new ArrayList<>();
		for(GenericWorker w: workerList){
			if(w.getLat() >= startLat && w.getLat() <= endLat && w.getLng() >= startLon && w.getLng() <= endLon){
				result.add(w);
			}
		}
		return result;
	}


	/**
	 * Compute input for one time instance, including container and
	 * invertedTable.
	 *
	 * Find the tasks whose regions contain the worker
	 *
	 * @param workerIdx
	 *            the worker idx
	 */
	public void reverseRangeQuery(final int workerIdx) {
		/* actual worker */
		GenericWorker w = workerList.get(workerIdx);

		/* task id, increasing from 0 to the number of task - 1 */
		int tid = 0;
		for (int i = 0; i < taskList.size(); i++) {
//			System.out.println(i + "xxx" + taskList.size());
//			System.out.println(taskList.get(i).getClass().toString() + i + " " + taskList.size());
			SensingTask task = (SensingTask) taskList.get(i);

			/* tick expired task */
			if ((TimeInstance - task.getArrivalTime()) >= (GeocrowdConstants.MAX_TASK_DURATION)
				) {
				task.setExpired();
			}
			/* if worker in task region */
			else if (GeocrowdTaskUtility.distanceWorkerTask(DATA_SET, w, task) <= task.getRadius()) {

				/* compute a list of candidate tasks */
				if (!taskSet.contains(tid)) {
					candidateTaskIndices.add(tid);
					taskSet.add(tid);
				}

				if (containerPrune[workerIdx] == null) {
					containerPrune[workerIdx] = new ArrayList();
				}
				containerPrune[workerIdx]
						.add(candidateTaskIndices.indexOf(tid));

				/**
				 * inverted container need to update after compute
				 * containerWorker if (!invertedContainer.containsKey(tid)) {
				 * invertedContainer.put(tid, new ArrayList() { {
				 * add(workerIdx); } }); } else {
				 * invertedContainer.get(tid).add(workerIdx); }
				 */
			}// if not overlapped

			tid++;
		}// for loop
	}
	
    /**
     * Re-initialize all parameters
     */
	public void reset() {
		totalEntropy = 0;
		maxEntropy = 0;
		meanEntropy = 0;
		TimeInstance = 0;
		TaskCount = 0;
		WorkerCount = 0;
		TotalAssignedTasks = 0;
		TotalCoveredUtility = 0.0;
		TotalAssignedWorkers = 0;
		workerList = null;
		workerList = new ArrayList<>();
		taskList = new ArrayList<>();
//		assignedTasks.clear();
	}

}
