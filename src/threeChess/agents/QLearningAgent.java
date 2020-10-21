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
 * @todo: Implement amInCheck, canPutOtherInCheck, update reward function to
 *        also check for center board control, piece mobility, and king saftey
 * 
 *        Q-Learning Transition Rule: Qnew(state_t, action_t) = Qold(state_t,
 *        action_t) + learningRate * (reward_t + maxQ(next_state,
 *        every_possible_action_in_next_state) - Qold(state_t, action_t))
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

    HashMap<Position, Double> pawnPositionValue; // Maps the position on the board with a pawn's relative value on that
                                                 // position
    HashMap<Position, Double> knightPositionValue; // Maps the position on the board with a knight's relative value on
                                                   // that position
    HashMap<Position, Double> bishopPositionValue; // Maps the position on the board with a bishop's relative value on
                                                   // that position
    HashMap<Position, Double> rookPositionValue; // Maps the position on the board with a rook's relative value on that
                                                 // position
    HashMap<Position, Double> queenPositionValue; // Maps the position on the board with a queen's relative value on
                                                  // that position
    HashMap<Position, Double> kingPositionValue; // Maps the position on the board with a king's relative value on that
                                                 // position

    boolean haveMoved;
    Colour myColour;

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
        haveMoved = false;

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

    /*
     * ------------------------------------------------------- Private Helper
     * Functions -------------------------------------------------------
     */

    private void init() {
        switch (myColour) {
            case BLUE:
                pawnPositionValue.put(Position.BA1, 0.0); pawnPositionValue.put(Position.BA2, 2.0);
                pawnPositionValue.put(Position.BA3, 2.0); pawnPositionValue.put(Position.BA4, 0.0);
                pawnPositionValue.put(Position.BB1, 0.0); pawnPositionValue.put(Position.BB2, 3.0);
                pawnPositionValue.put(Position.BB3, -2.0); pawnPositionValue.put(Position.BB4, 0.0);
                pawnPositionValue.put(Position.BC1, 0.0); pawnPositionValue.put(Position.BC2, 3.0);
                pawnPositionValue.put(Position.BC3, -2.0); pawnPositionValue.put(Position.BC4, 0.0);
                pawnPositionValue.put(Position.BD1, 0.0); pawnPositionValue.put(Position.BD2, -3.5);
                pawnPositionValue.put(Position.BD3, 0.0); pawnPositionValue.put(Position.BD4, 4.5);
                pawnPositionValue.put(Position.BE1, 0.0); pawnPositionValue.put(Position.BE2, -3.5);
                pawnPositionValue.put(Position.BE3, 0.0); pawnPositionValue.put(Position.BE4, 4.5);
                pawnPositionValue.put(Position.BF1, 0.0); pawnPositionValue.put(Position.BF2, 3.0);
                pawnPositionValue.put(Position.BF3, -2.0); pawnPositionValue.put(Position.BF4, 0.0);
                pawnPositionValue.put(Position.BG1, 0.0); pawnPositionValue.put(Position.BG2, 3.0);
                pawnPositionValue.put(Position.BG3, -2.0); pawnPositionValue.put(Position.BG4, 0.0);
                pawnPositionValue.put(Position.BH1, 0.0); pawnPositionValue.put(Position.BH2, 2.0);
                pawnPositionValue.put(Position.BH3, 2.0); pawnPositionValue.put(Position.BH4, 0.0);

                pawnPositionValue.put(Position.GH4, 2.0); pawnPositionValue.put(Position.GH3, 2.5);
                pawnPositionValue.put(Position.GH2, 4.0); pawnPositionValue.put(Position.GH1, 4.2);
                pawnPositionValue.put(Position.GG4, 2.0); pawnPositionValue.put(Position.GG3, 2.5);
                pawnPositionValue.put(Position.GG2, 4.0); pawnPositionValue.put(Position.GG1, 4.2);
                pawnPositionValue.put(Position.GF4, 2.0); pawnPositionValue.put(Position.GF3, 2.7);
                pawnPositionValue.put(Position.GF2, 4.0); pawnPositionValue.put(Position.GF1, 4.2);
                pawnPositionValue.put(Position.GE4, 2.0); pawnPositionValue.put(Position.GE3, 3.0);
                pawnPositionValue.put(Position.GE2, 4.0); pawnPositionValue.put(Position.GE1, 4.2);
                pawnPositionValue.put(Position.GD4, 2.0); pawnPositionValue.put(Position.GD3, 2.5);
                pawnPositionValue.put(Position.GD2, 4.0); pawnPositionValue.put(Position.GD1, 4.2);
                pawnPositionValue.put(Position.GC4, 2.0); pawnPositionValue.put(Position.GC3, 2.7);
                pawnPositionValue.put(Position.GC2, 4.0); pawnPositionValue.put(Position.GC1, 4.2);
                pawnPositionValue.put(Position.GB4, 2.0); pawnPositionValue.put(Position.GB3, 2.5);
                pawnPositionValue.put(Position.GB2, 4.0); pawnPositionValue.put(Position.GB1, 4.2);
                pawnPositionValue.put(Position.GA4, 2.0); pawnPositionValue.put(Position.GA3, 2.5);
                pawnPositionValue.put(Position.GA2, 4.0); pawnPositionValue.put(Position.GA1, 4.2);

                pawnPositionValue.put(Position.RH4, 2.0); pawnPositionValue.put(Position.RH3, 2.5);
                pawnPositionValue.put(Position.RH2, 4.0); pawnPositionValue.put(Position.RH1, 4.2);
                pawnPositionValue.put(Position.RH4, 2.0); pawnPositionValue.put(Position.RG3, 2.5);
                pawnPositionValue.put(Position.RG2, 4.0); pawnPositionValue.put(Position.RG1, 4.2);
                pawnPositionValue.put(Position.RF4, 2.0); pawnPositionValue.put(Position.RF3, 2.7);
                pawnPositionValue.put(Position.RF2, 4.0); pawnPositionValue.put(Position.RF1, 4.2);
                pawnPositionValue.put(Position.RE4, 2.0); pawnPositionValue.put(Position.RE3, 3.0);
                pawnPositionValue.put(Position.RE2, 4.0); pawnPositionValue.put(Position.RE1, 4.2);
                pawnPositionValue.put(Position.RD4, 2.0); pawnPositionValue.put(Position.RD3, 2.5);
                pawnPositionValue.put(Position.RD2, 4.0); pawnPositionValue.put(Position.RD1, 4.2);
                pawnPositionValue.put(Position.RC4, 2.0); pawnPositionValue.put(Position.RC3, 2.7);
                pawnPositionValue.put(Position.RC2, 4.0); pawnPositionValue.put(Position.RC1, 4.2);
                pawnPositionValue.put(Position.RB4, 2.0); pawnPositionValue.put(Position.RB3, 2.5);
                pawnPositionValue.put(Position.RB2, 4.0); pawnPositionValue.put(Position.RB1, 4.2);
                pawnPositionValue.put(Position.RA4, 2.0); pawnPositionValue.put(Position.RA3, 2.5);
                pawnPositionValue.put(Position.RA2, 4.0); pawnPositionValue.put(Position.RA1, 4.2);



                break;
                
            case RED:
                pawnPositionValue.put(Position.RA1, 0.0); pawnPositionValue.put(Position.RA2, 2.0);
                pawnPositionValue.put(Position.RA3, 2.0); pawnPositionValue.put(Position.RA4, 0.0);
                pawnPositionValue.put(Position.RB1, 0.0); pawnPositionValue.put(Position.RB2, 3.0);
                pawnPositionValue.put(Position.RB3, -2.0); pawnPositionValue.put(Position.RB4, 0.0);
                pawnPositionValue.put(Position.RC1, 0.0); pawnPositionValue.put(Position.RC2, 3.0);
                pawnPositionValue.put(Position.RC3, -2.0); pawnPositionValue.put(Position.RC4, 0.0);
                pawnPositionValue.put(Position.RD1, 0.0); pawnPositionValue.put(Position.RD2, -3.5);
                pawnPositionValue.put(Position.RD3, 0.0); pawnPositionValue.put(Position.RD4, 4.5);
                pawnPositionValue.put(Position.RE1, 0.0); pawnPositionValue.put(Position.RE2, -3.5);
                pawnPositionValue.put(Position.RE3, 0.0); pawnPositionValue.put(Position.RE4, 4.5);
                pawnPositionValue.put(Position.RF1, 0.0); pawnPositionValue.put(Position.RF2, 3.0);
                pawnPositionValue.put(Position.RF3, -2.0); pawnPositionValue.put(Position.RF4, 0.0);
                pawnPositionValue.put(Position.RG1, 0.0); pawnPositionValue.put(Position.RG2, 3.0);
                pawnPositionValue.put(Position.RG3, -2.0); pawnPositionValue.put(Position.RG4, 0.0);
                pawnPositionValue.put(Position.RH1, 0.0); pawnPositionValue.put(Position.RH2, 2.0);
                pawnPositionValue.put(Position.RH3, 2.0); pawnPositionValue.put(Position.RH4, 0.0);

                pawnPositionValue.put(Position.GH4, 2.0); pawnPositionValue.put(Position.GH3, 2.5);
                pawnPositionValue.put(Position.GH2, 4.0); pawnPositionValue.put(Position.GH1, 4.2);
                pawnPositionValue.put(Position.GG4, 2.0); pawnPositionValue.put(Position.GG3, 2.5);
                pawnPositionValue.put(Position.GG2, 4.0); pawnPositionValue.put(Position.GG1, 4.2);
                pawnPositionValue.put(Position.GF4, 2.0); pawnPositionValue.put(Position.GF3, 2.7);
                pawnPositionValue.put(Position.GF2, 4.0); pawnPositionValue.put(Position.GF1, 4.2);
                pawnPositionValue.put(Position.GE4, 2.0); pawnPositionValue.put(Position.GE3, 3.0);
                pawnPositionValue.put(Position.GE2, 4.0); pawnPositionValue.put(Position.GE1, 4.2);
                pawnPositionValue.put(Position.GD4, 2.0); pawnPositionValue.put(Position.GD3, 2.5);
                pawnPositionValue.put(Position.GD2, 4.0); pawnPositionValue.put(Position.GD1, 4.2);
                pawnPositionValue.put(Position.GC4, 2.0); pawnPositionValue.put(Position.GC3, 2.7);
                pawnPositionValue.put(Position.GC2, 4.0); pawnPositionValue.put(Position.GC1, 4.2);
                pawnPositionValue.put(Position.GB4, 2.0); pawnPositionValue.put(Position.GB3, 2.5);
                pawnPositionValue.put(Position.GB2, 4.0); pawnPositionValue.put(Position.GB1, 4.2);
                pawnPositionValue.put(Position.GA4, 2.0); pawnPositionValue.put(Position.GA3, 2.5);
                pawnPositionValue.put(Position.GA2, 4.0); pawnPositionValue.put(Position.GA1, 4.2);

                pawnPositionValue.put(Position.BH4, 2.0); pawnPositionValue.put(Position.BH3, 2.5);
                pawnPositionValue.put(Position.BH2, 4.0); pawnPositionValue.put(Position.BH1, 4.2);
                pawnPositionValue.put(Position.BH4, 2.0); pawnPositionValue.put(Position.BG3, 2.5);
                pawnPositionValue.put(Position.BG2, 4.0); pawnPositionValue.put(Position.BG1, 4.2);
                pawnPositionValue.put(Position.BF4, 2.0); pawnPositionValue.put(Position.BF3, 2.7);
                pawnPositionValue.put(Position.BF2, 4.0); pawnPositionValue.put(Position.BF1, 4.2);
                pawnPositionValue.put(Position.BE4, 2.0); pawnPositionValue.put(Position.BE3, 3.0);
                pawnPositionValue.put(Position.BE2, 4.0); pawnPositionValue.put(Position.BE1, 4.2);
                pawnPositionValue.put(Position.BD4, 2.0); pawnPositionValue.put(Position.BD3, 2.5);
                pawnPositionValue.put(Position.BD2, 4.0); pawnPositionValue.put(Position.BD1, 4.2);
                pawnPositionValue.put(Position.BC4, 2.0); pawnPositionValue.put(Position.BC3, 2.7);
                pawnPositionValue.put(Position.BC2, 4.0); pawnPositionValue.put(Position.BC1, 4.2);
                pawnPositionValue.put(Position.BB4, 2.0); pawnPositionValue.put(Position.BB3, 2.5);
                pawnPositionValue.put(Position.BB2, 4.0); pawnPositionValue.put(Position.BB1, 4.2);
                pawnPositionValue.put(Position.BA4, 2.0); pawnPositionValue.put(Position.BA3, 2.5);
                pawnPositionValue.put(Position.BA2, 4.0); pawnPositionValue.put(Position.BA1, 4.2);

                break;

            case GREEN:

                break;

            default:
                break;
        }
    }

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
     * 
     * @return true if under check, otherwise false
     */
    private boolean amUnderCheck() {

    }

    /**
     * Checks to see whether we can put an opponent in check
     * 
     * @return true if we can put an opponent in check, otherwise false
     */
    private boolean canPutOtherInCheck() {

    }

    private double calculateCurrentReward() {
        double curRewardValue = curBoardState.score(curBoardState.getTurn());

        if (prevRewardValue == 0) {
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

    /*
     * ------------------------------------------------------- Public Functions
     * -------------------------------------------------------
     */

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
        if (!haveMoved) {
            haveMoved = true;
            myColour = board.getTurn();
            init();
        }
        curBoardState = board;
        curReward = calculateCurrentReward();
        update();
        if (board.gameOver()) { // This should never happen... But just in case
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
