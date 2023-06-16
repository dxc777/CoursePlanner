package Application;

import java.util.ArrayList;
import java.util.HashMap;

import Exceptions.IllegalStructure;
import GraphFiles.AdjList;
import GraphFiles.Edge;

public class Scheduler
{
	private ArrayList<Course> courseList;
	
	private AdjList classStructure;
	
	private AdjList prereqGraph;
	
	private int currentSemester;
	
	private static final int PREREQUISITE_TO_THIS_VERTEX = 1;
	
	private static final int  PREREQUISITE_FOR_ANOTHER_VERTEX= 2;
	
	public Scheduler(Parser parsedFile) 
	{
		this.courseList = parsedFile.getCourseList();
		classStructure = new AdjList(courseList.size());
		prereqGraph = new AdjList(courseList.size());
		currentSemester = 1;
		
		buildGraph(parsedFile.getIdToVertex(), parsedFile.getRawPrerequisites());
		checkIfCycle();
		markInitialClasses();
	}
	public String getAvailableClasses() 
	{
		StringBuilder s = new StringBuilder();
		int listIndex = 1;
		s.append("Available Classes for Semester: " + currentSemester + "\n");
		for(int i = 0; i < courseList.size(); i++) 
		{
			if(canBeTaken(i)) 
			{
				s.append(listIndex + ")" + courseList.get(i).toString() + "\n");
				listIndex++;
			}
		}
		return s.toString();
	}
	
	//Index is in the range of 1 - n where n is the number of courses in the list
	public int convertIndexToVertex(int index) 
	{
		if(index <= 0 || index >= courseList.size()) {
			return -1;
		}
		int i = 0;
		
		while(i < courseList.size() && index != 0) 
		{
			if(canBeTaken(i)) 
			{
				index--;
			}
			if(index != 0) 
			{
				i++;
			}
		}
		return i == courseList.size() ? -1 : i;
	}
	/**
	 * The course scheduler is essentially a list of lists of courses 
	 * There is a list for each semester that the student is in school for.
	 * The list contains the courses that the student chose to take that semester
	 * The purpose of this function is mark the semester the class was taken 
	 * and then remove the prerequisite of this class from other courses
	 * Assume that when this funciton is called it is a valid vertex that cna be taken
	 * @param vertex
	 */
	public void addClassToSemester(int vertex) 
	{
		Course takenCourse = courseList.get(vertex);
		takenCourse.semesterClassCompleted = currentSemester;
		Edge currEdge = classStructure.getNeighborList(vertex).next;
		while(currEdge != null) 
		{
			if(currEdge.weight == PREREQUISITE_FOR_ANOTHER_VERTEX) 
			{
				prereqGraph.removeEdge(currEdge.vertex, vertex);
				if(prereqGraph.getNeighborList(currEdge.vertex).next == null) 
				{
					courseList.get(currEdge.vertex).semesterPrereqCompleted = currentSemester;
				}
			}
		}
	}
	
	
	/*
	 * Return whether or not a class can be taken during a certain semester.
	 * It checks if the class prerequisites have been completed, the class has not been taken,
	 * and that the prerequisite have been completed before the current semster
	 */
	private boolean canBeTaken(int vertex) 
	{
		Course course = courseList.get(vertex);
		return currentSemester > course.semesterPrereqCompleted &&
				course.semesterClassCompleted == Course.COURSE_NOT_TAKEN;
	}
	
	private void markInitialClasses() 
	{
		for(int i = 0; i < prereqGraph.nodeCount; i++) 
		{
			if(prereqGraph.getNeighborList(i).next == null) 
			{
				courseList.get(i).semesterPrereqCompleted = 0;
			}
		}
	}
	
	private void buildGraph(HashMap<String, Integer> idToVertex, ArrayList<ArrayList<String>> rawPrerequisites) 
	{
		for(int i = 0; i < rawPrerequisites.size(); i++) 
		{
			ArrayList<String> prerequisitesForVertex = rawPrerequisites.get(i);
			for(String s : prerequisitesForVertex) 
			{
				Integer prereqVertex = idToVertex.get(s);
				if(prereqVertex == null)
				{
					System.err.println("The given prerequisite " + s +" for class " + courseList.get(i).courseName + " was not declared in the input file");
					System.exit(0);
				}
				classStructure.addEdge(i, prereqVertex, PREREQUISITE_TO_THIS_VERTEX);
				classStructure.addEdge(prereqVertex, i, PREREQUISITE_FOR_ANOTHER_VERTEX);
				prereqGraph.addEdge(i, prereqVertex, PREREQUISITE_TO_THIS_VERTEX);
			}
		}
	}
	
	private static int UNVISITED = 0;
	private static int IN_STACK = 1;
	private static int VISITED = 2;
	
	private void checkIfCycle() 
	{
		int[] visited = new int[courseList.size()];
		ArrayList<Integer> vertexesInCycle = new ArrayList<>();
		for(int i = 0; i < courseList.size(); i++) 
		{
			if(visited[i] == UNVISITED && containsCycleDFS(i,visited,vertexesInCycle)) 
			{
				String cycleError = buildErrorMessage(vertexesInCycle);
				System.err.println(cycleError);
				System.exit(0);
			}
		}
	}
	

	private boolean containsCycleDFS(int vertex, int[] visited, ArrayList<Integer> vertexesInCycle) 
	{
		visited[vertex] = IN_STACK;
		Edge currEdge = prereqGraph.getNeighborList(vertex).next;
		while(currEdge != null) 
		{
			if(visited[currEdge.vertex] == UNVISITED) 
			{
				if(containsCycleDFS(currEdge.vertex,visited,vertexesInCycle)) 
				{
					if(vertexesInCycle.get(0) != vertexesInCycle.get(vertexesInCycle.size() - 1))
					{
						vertexesInCycle.add(vertex);
					}
					return true;
				}
			}
			else if(visited[currEdge.vertex] == IN_STACK) 
			{
				vertexesInCycle.add(currEdge.vertex);
				vertexesInCycle.add(vertex);
				return true;
			}
			currEdge = currEdge.next;
		}
		visited[vertex] = VISITED;
		return false;
	}	
	
	private String buildErrorMessage(ArrayList<Integer> vertexesInCycle)
	{
		StringBuilder s = new StringBuilder();
		String course1 = courseList.get(0).courseName;
		String course2 = courseList.get(1).courseName;
		s.append("Error: There is a cycle in the provided course structure.\n");
		s.append("The course " + course2 + " requires " + course1 + " to be taken before it can be taken."
				+ "\nHowever, " + course1 + " also requires " + course2 + " to be taken before it at some point. "
				+ "\nThus this provided ordering makes taking the two courses to be taken impossible.");
		s.append("\nHere is the cycle:\n");
		for(int i = 1; i < vertexesInCycle.size(); i++) 
		{
			course2 = courseList.get(vertexesInCycle.get(i)).courseName;
			s.append(course2 + " requires " + course1 + "\n");
			course1 = course2;
		}
		return s.toString();
	}

	public ArrayList<Course> getCourseList()
	{
		return courseList;
	}

	public int getCurrentSemester()
	{
		return currentSemester;
	}
	
	//TODO: consider changing data type of semester to double to use the value Double.POSITIVE_INFINITY
	//instead of Integer.MAX_VALUE
	public boolean setCurrentSemester(int currentSemester) 
	{
		if(currentSemester < 0 || currentSemester == Course.PREREQ_NOT_COMPLETED) 
		{
			return false;
		}
		this.currentSemester = currentSemester;
		return true;
	}

}
