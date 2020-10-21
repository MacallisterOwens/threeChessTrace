package threeChess.agents;

import threeChess.*;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;

class StateAction implements Serializable {
    /**
     * Auto generated serialVersionUID
     */
    private static final long serialVersionUID = -2710175235150454155L;
    HashSet<Position> state; // Contains all positions of all pieces
    Position[] action; // The action taken in the given state

    public StateAction(Board board, Position[] action) {
        state = (HashSet<Position>) board.getPositionPieceMap().keySet();
        this.action = action;
    }
}

/**
 * @todo: Implement amInCheck, canPutOtherInCheck, update reward function to also check for center board control, piece mobility, and king saftey
 * 
 * Q-Learning Transition Rule: Qnew(state_t, action_t) = Qold(state_t, action_t)
 * + learningRate * (reward_t + maxQ(next_state,
 * every_possible_action_in_next_state) - Qold(state_t, action_t))
 **/
public class QLearningAgent extends Agent {

    private static final String name = "Q-Learning Agent";
    private static final Random random = new Random();

    private final String qTableStorage = "Q-Table-Storage"; // The name of the file storing the qTable object
    private final String nTimesExecutedStorage = "n-Times-Executed-Storage"; // The name of the file storing the
                                                                             // nTimesExecuted object

    private final double initLearningRate = 1.0; // The initial learning rate 1.0 == %100
    private final double dropChange = 0.95; // The change in the learning rate per drop
    private final double dropRate = 1.0; // The rate at which the learning rate drops
    private double epsilon = 1.0; // The probability in which we choose to utilize exploration vs exploitation

    Position[] myLastAction; // The last action *I* made
    Position[] prevSAAction; // Previous state-action-value action/move
    Board prevBoardState; // The state of the board in the previous move
    Board curBoardState; // The state of the board as it currently is
    double reward; // The reward of the previous action
    double curReward; // The reward we get for being on the current state from last action
    double prevRewardValue; // The value of our pieces + our taken pieces + board position from the prev
                            // board state

    HashMap<StateAction, Double> qTable; // The mapping of every single state-action pair to its value
    HashMap<StateAction, Integer> nTimesExecuted; // The mapping of every single state-action pair to the number of
                                                  // times that action has been taken in that state

    /**
     * A no argument constructor, required for tournament management.
     **/
    public QLearningAgent() {
        myLastAction = new Position[] { null, null };
        prevSAAction = new Position[] { null, null };
        prevBoardState = null;
        curBoardState = null;
        reward = 0;
        curReward = 0;
        prevRewardValue = 0;

        qTable = new HashMap<StateAction, Double>();
        nTimesExecuted = new HashMap<StateAction, Integer>();

        try {
            FileInputStream qFileIn = new FileInputStream(qTableStorage);
            ObjectInputStream qObjectIn = new ObjectInputStream(qFileIn);
            qTable = (HashMap<StateAction, Double>) qObjectIn.readObject();
            qFileIn.close();
            qObjectIn.close();

            FileInputStream nFileIn = new FileInputStream(nTimesExecutedStorage);
            ObjectInputStream nObjectIn = new ObjectInputStream(nFileIn);
            nTimesExecuted = (HashMap<StateAction, Integer>) nObjectIn.readObject();
            nFileIn.close();
            nObjectIn.close();
        } catch (Exception e) {
            System.out.println(e);
            System.out.print("Q Table storage or n Times Executed Storage either do not exist or could not be opened.");
        }

    }

    
    /* ------------------------------------------------------- Private Helper Functions ------------------------------------------------------- */

    /**
     * Returns a set of all availble moves for a given piece Moves are represented
     * with the standard {start, end} position array
     * 
     * @param board    representation of the current game state
     * @param position representation of where the given piece is located on the
     *                 board
     * @return a set containing all possible moves for a given piece
     *
     **/
    private HashSet<Position[]> getAvailableMoves(Board board, Position position) {
        Piece mover = board.getPiece(position);
        PieceType moverType = mover.getType();

        HashSet<Position[]> possibleMoves = new HashSet<Position[]>();
        Position p;

        for (Direction[] moves : moverType.getSteps()) { // iterate over every possible step a piece can make
            p = position;
            for (int i = 1; i <= moverType.getStepReps(); i++) { // iterate that step as many times as possible to check
                                                                 // every possible move
                try { // try the move
                    p = board.step(mover, moves, p);
                    if (board.isLegalMove(position, p)) {
                        possibleMoves.add(new Position[] { position, p }); // move is legal, add it to the list
                    }
                } catch (ImpossiblePositionException e) {
                    break; // ended up in an illegal position, abandon that step and try another
                }

            }
        }
        return possibleMoves;
    }

    /**
     * returns a HashMap of the position of every piece the current player has on
     * the board mapped to a set of all possible moves
     * 
     * @param board The current board state
     * @return a HashMap mapping every pieces position to a set of valid moves
     */
    private HashMap<Position, HashSet<Position[]>> getAllAvailableMoves(Board boardState) {
        HashMap<Position, HashSet<Position[]>> allMoves = new HashMap<Position, HashSet<Position[]>>();
        for (Position pos : boardState.getPositions(boardState.getTurn())) {
            allMoves.put(pos, getAvailableMoves(boardState, pos));
        }
        return allMoves;
    }

    /**
     * This function determines the learning rate for the Q function which decreases
     * over time, following a step-based learning schedule
     * 
     * @param nVisited The number of times that the state-action pair has been
     *                 visited
     * @return The learning rate paramater which follows a step-based learning
     *         schedule
     */
    private double getLearningRate(int nVisited) {
        return (initLearningRate * Math.pow(dropChange, (1 + nVisited) / dropRate));
    }

    /**
     * Determines whether or not we should choose an exploration or explotative next
     * step. Starts by favouring exploring during the early stages then transitions
     * to exploiting later on in the game
     * 
     * @return true if we should choose exploration, otherwise false
     */
    private boolean shouldExplore() {
        if (random.nextDouble() < epsilon) {
            epsilon *= 0.95;
            return true;
        } else {
            epsilon *= 0.95;
            return false;
        }
    }

    private double argMaxQ(Board boardState) {
        HashMap<Position, HashSet<Position[]>> availMoves = getAllAvailableMoves(boardState);

        double maxEstUtility = Double.MIN_VALUE;

        for (Map.Entry<Position, HashSet<Position[]>> entry : availMoves.entrySet()) {
            HashSet<Position[]> pieceMoves = entry.getValue();
            for (Position[] action : pieceMoves) {
                StateAction curExaminedSA = new StateAction(boardState, action);
                if (nTimesExecuted.containsKey(curExaminedSA)) { // If we have seen the state-action pair already use
                                                                 // its existing q-value
                    if (qTable.get(curExaminedSA) > maxEstUtility) {
                        maxEstUtility = qTable.get(curExaminedSA);
                    }
                } else { // Add the new state-action pair to the q-table and set its value to 0
                    qTable.put(curExaminedSA, 0.0);
                    nTimesExecuted.put(curExaminedSA, 0);
                    if (qTable.get(curExaminedSA) > maxEstUtility) {
                        maxEstUtility = 0.0;
                    }
                }
            }
        }

        return maxEstUtility;
    }

    /**
     * The update function for the QLearningAgent. Uses temporal-difference learning
     * (TDL).
     */
    private void update() {
        if (curBoardState.gameOver()) { // If the game is over add this state to the table if it does not already exist
            qTable.putIfAbsent(new StateAction(curBoardState, null), curReward);
        }
        if (prevBoardState != null) { // If we have already seen a previous state
            StateAction curSA = new StateAction(prevBoardState, prevSAAction);
            double learningRate; // The learning rate to be used in the update function
            double curQValue; // The current Q value of curSA
            if (nTimesExecuted.putIfAbsent(curSA, 1) != null) { // If curSA is in nTimesExecuted update the times its
                                                                // been seen by one
                int nTimesSeen = nTimesExecuted.get(curSA) + 1;
                nTimesExecuted.put(curSA, nTimesSeen);
                learningRate = getLearningRate(nTimesSeen);
                curQValue = qTable.get(curSA);
                qTable.put(curSA, (curQValue + (learningRate * (reward + argMaxQ(curBoardState) - curQValue))));
            } else { // Add curSA to the q-table
                learningRate = getLearningRate(1);
                qTable.put(curSA, (0.0 + (learningRate) * (reward + argMaxQ(curBoardState) - 0.0)));
            }

        }
    }

    /**
     * Checks to see if we are currently under check
     * @return true if under check, otherwise false
     */
    private boolean amUnderCheck() {

    }

    private double calculateCurrentReward() {
        double curRewardValue = curBoardState.score(curBoardState.getTurn());

        if(prevRewardValue == 0) {
            prevRewardValue = prevBoardState.score(prevBoardState.getTurn());
        }

        double soln = curRewardValue - prevRewardValue;
        prevRewardValue = curRewardValue;

        return soln;
    }

    /**
     * Writes the qTable to the file qTableStorage and writes nTimesExecuted to the
     * file nTimesExecutedStorage
     * 
     * @return true if sucessful write, otherwise false
     */
    private boolean storeData() {
        try {
            FileOutputStream qFileOut = new FileOutputStream(qTableStorage);
            ObjectOutputStream qObjectOut = new ObjectOutputStream(qFileOut);
            qObjectOut.writeObject(qTable);
            qFileOut.close();
            qObjectOut.close();

            FileOutputStream nFileOut = new FileOutputStream(nTimesExecutedStorage);
            ObjectOutputStream nObjectOut = new ObjectOutputStream(nFileOut);
            qObjectOut.writeObject(nTimesExecuted);
            nFileOut.close();
            nObjectOut.close();
            return true;
        } catch (Exception e) {
            System.out.println(e);
            System.out.print("Q Table storage or n Times Executed Storage could not be written to disk.");
            return false;
        }
    }

    /* ------------------------------------------------------- Public Functions -------------------------------------------------------*/

    /**
     * Play a move in the game. The agent is given a Board Object representing the
     * position of all pieces, the history of the game and whose turn it is. They
     * respond with a move represented by a pair (two element array) of positions:
     * the start and the end position of the move.
     * 
     * @param board The representation of the game state.
     * @return a two element array of Position objects, where the first element is
     *         the current position of the piece to be moved, and the second element
     *         is the position to move that piece to.
     **/
    public Position[] playMove(Board board) {
        curBoardState = board;
        curReward = calculateCurrentReward();
        update();
        if(board.gameOver()) { // This should never happen... But just in case
            return null;
        }


        return new Position[] { null, null };
    }

    /**
     * @return the Agent's name, for annotating game description.
     **/
    public String toString() {
        return name;
    }

    /**
     * Displays the final board position to the agent, if required for learning
     * purposes. Other a default implementation may be given.
     * 
     * @param finalBoard the end position of the board
     **/
    public void finalBoard(Board finalBoard) {
    }

}
