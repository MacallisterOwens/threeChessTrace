package threeChess.agents;

import threeChess.*;
import java.util.*;
import java.lang.Math;


public class MonteCarloAgent extends Agent {

    private static final String name = "Carlos";
    private Board workingBoard; //board to modify while traversing the game tree to validate moves
    //Manually keep track of piece position to avoid calling getPositions()
    private HashMap<Piece, Position> redPieceLocations; 
    private HashMap<Piece, Position> bluePieceLocations; 
    private HashMap<Piece, Position> greenPieceLocations; 
    //Store piece locations to enable fast lookups for selection phase of MCTS
    private HashMap<Piece, Position> redWorkingPieceLocations; 
    private HashMap<Piece, Position> blueWorkingPieceLocations; 
    private HashMap<Piece, Position> greenWorkingPieceLocations; 
    private GameTree gameTree;

    //Manage time effectively
    private static long idealMoves = 40L;
    private int mcRounds = 0;

    //Helper variable to store features of any game instance
    private Colour playerColour;
    private Position kingHomePosition;

    //Rollout agents
    private static final Random random = new Random();

    //Empty constructor
    public MonteCarloAgent() {}

    public Position[] playMove(Board board) {

        this.initPieceLocations(board);
        this.mcRounds = 0;
        if (board.getMoveCount() < 3) this.initialiseAgent(board);
        this.gameStateUpdate(board);

        long startTime = System.nanoTime(); 
        while ( (System.nanoTime() - startTime)/1_000_000L < board.getTimeLeft(this.playerColour)/idealMoves) {
            MCTSRound(board);
            System.out.println(++this.mcRounds + "MC rounds\r");
        }

        return gameTree.selectMove();
    }


    // MCTS Methods ----------------------------------------------------------------------------

    public void MCTSRound(Board board) {

        this.resetWorkingPieceLocations();
        this.gameTree.resetTraversal();
        this.resetWorkingBoard(board);
        this.selection();

    }

    /**
     * Choose a child node to expand to game completion
     * We wish to find a 0/0 node to begin a new rollout through
     * Every 0/0 node that is selected by this method has its children fully expanded as new 0/0 nodes
     * @throws RuntimeException if traversal fails at any point or the tree is inconsistent with workingboard
     */
    private void selection() {

        //Traverse the tree until we reach a twig node
        Position[] nextMove;
        while(this.gameTree.traversalAtLeaf() == false) {

            //get next child to traverse to
            nextMove = gameTree.uctSelectChild();
            //update workingBoard and piecePositions
            try {
                this.workingBoardMove(nextMove);
            } catch (ImpossiblePositionException e) {throw new RuntimeException("traversal has failed, tree has been corrupted");}
            if (this.gameTree.traverse(nextMove) == false) {
                throw new RuntimeException("traversal has failed, tree has been corrupted");
            };
        }

        //the traversal node is now some 0/0 node
        //first we fully expand this node

        //Check that the node does not end the game decisively
        if (this.workingBoard.gameOver()) {
            this.backPropogate(workingBoard.getWinner(), workingBoard.getLoser());
        } else {

            this.expandNode();
            //Done, traversalNode is now ready to have a rollout started from its game state.
            this.rollout();
        }
    }

    /**
     * Expand the current traversalNode with all the possible moves that can be made
     */
    private void expandNode() {
        //Get the player turn in the game state described by traversalNode/workingBoard
        Colour player = gameTree.getTraversalPlayer();
        if (player != workingBoard.getTurn()) throw new RuntimeException("AAA");
        //These moves should be valid & legal
        HashSet<Position[]> availableMoves = this.getAllAvailableMoves(player);

        for (Position[] move : availableMoves) {
            gameTree.expandTraversalNode(move);
        }
    }


    /**
     * Simulate a game from the 0/0 node selected
     * The game state to begin from is described by workingBoard and traversalNode
     */
    private void rollout() {
        //workingBoard holds the position from which we wish to initiate the rollout
        //for the moment, just use a light rollout (random)

        //Note that it is assumed the agent will return only valid moves


        while (!workingBoard.gameOver()) {
            Position[] move = this.fastRandomRollout();
            try {this.workingBoardMove(move);}
            catch (ImpossiblePositionException e) {System.out.println("DEBUG: rollout agent played illegal move");}
        }

        //DEBUGSystem.out.println(workingBoard.getWinner());
        this.backPropogate(workingBoard.getWinner(), workingBoard.getLoser());
    }

    /**
     * Update the tree based on the current rollout result
     * @param winner the winner of the rollout
     * @param loser the loser of the rollout
     */
    private void backPropogate(Colour winner, Colour loser) {
        while (true) {
            gameTree.updateTraversal(winner, loser);
            if (gameTree.traversalAtRoot()) break;
            gameTree.traverseBack();
        }
    }



    //GameTree methods --------------------------------------------------------------------------------------------

    /**
     * Initialises the game tree
     **/
    private void initGameTree() {
        this.gameTree = new GameTree(this.playerColour);
        this.expandNode();

    }

    /**
     * Catches gameTree up after the opponents have moved
     * @param moves the moves that have been played since our last move
     */
    private void updateGameTree(Position[][] moves) {
            gameTree.mctsPrune(moves);
            if (gameTree.traversalAtLeaf()) {
                this.expandNode();
            }

    }

    //Board, piece and move methods -------------------------------------------------------------------------------

    private boolean validateMove(Position[] move) {return this.workingBoard.isLegalMove(move[0], move[1]);}


    /**
     * Method to make moves on the working board during selection
     * Also updates workingPiecePositions as it goes
     * @throws IllegalArgumentException if the move is not legal/valid
     */
    private void workingBoardMove(Position[] move) throws ImpossiblePositionException {

        //Check if move is legal
        if (this.validateMove(move) == false) {
            throw new IllegalArgumentException("Illegal move sent to workingBoardMove");
        }

        Position start = move[0];
        Position end = move[1];
        Piece mover = workingBoard.getPiece(start);
        Piece captured = workingBoard.getPiece(end);
        Colour movePlayer = mover.getColour();
        HashMap<Piece, Position> playerPieceLocations = this.getPlayerPieceLocations(movePlayer);

        //Update the relevant workingPieceLocations hashmap
        //There are a number of conditions to check

        //First: check for castling
        //Borrows code form Board.java to perform the check
        playerPieceLocations.remove(mover);
        if (mover.getType()==PieceType.KING && start == this.kingHomePosition) {
            playerPieceLocations.put(mover, end);
            if(end.getColumn()==2) {//castle left
                Position rookPos = Position.get(movePlayer, 0, 0);
                Piece rook = workingBoard.getPiece(rookPos);
                playerPieceLocations.put(rook, Position.get(movePlayer, 0 ,3));
            } else if(end.getColumn()==6) {//castle right
                Position rookPos = Position.get(movePlayer, 0, 7);
                Piece rook = workingBoard.getPiece(rookPos);
                playerPieceLocations.put(rook, Position.get(movePlayer, 0, 5));
            }
        //Second: check if the move results in a capture
        } if (captured != null) {
            this.getPlayerPieceLocations(captured.getColour()).remove(captured);
        } if (mover.getType() == PieceType.PAWN && end.getRow() == 0 && end.getColour() != movePlayer) {
            //Need to move first to ensure that the piece object stored in the hashmap
            //is identical to the one created in workingBoard
            this.workingBoard.move(start, end);
            playerPieceLocations.put(this.workingBoard.getPiece(end), end);
        } else {
            //Normal move
            playerPieceLocations.put(mover, end);
            //Now play move
            this.workingBoard.move(start, end);
        }

    }

    /**
     * Resets the working board
     * @param board the board to reset workingBoard to
     */
    private void resetWorkingBoard(Board board) {
        try {this.workingBoard = (Board) board.clone();}
        catch (CloneNotSupportedException e) {System.out.println("DEBUG: working board reset failed");}
    }

    /**
     * Reset workingPieceLocations to match the initila game state
     */
    private void resetWorkingPieceLocations() {
        this.redWorkingPieceLocations = new HashMap<Piece, Position>();
        this.blueWorkingPieceLocations = new HashMap<Piece, Position>();
        this.greenWorkingPieceLocations = new HashMap<Piece, Position>();
        this.redWorkingPieceLocations.putAll(this.redPieceLocations);
        this.blueWorkingPieceLocations.putAll(this.bluePieceLocations);
        this.greenWorkingPieceLocations.putAll(this.greenPieceLocations);
    }

    /**Method to initliase the pieceLocation HashMap
     * @param board the game state to inform the initialization
     */
    private void initPieceLocations(Board board) {
        this.redPieceLocations = new HashMap<Piece, Position>();
        this.bluePieceLocations = new HashMap<Piece, Position>();
        this.greenPieceLocations = new HashMap<Piece, Position>();
        //Use the board getPositions() method to add each players pieces to each hashmap
        //Need to reverse the representation returned by getPositions (<Position, Piece> to <Piece, Position>)
        for (Position p : board.getPositions(Colour.RED)) {
            this.redPieceLocations.put(board.getPiece(p), p);
        } for (Position p : board.getPositions(Colour.BLUE)) {
            this.bluePieceLocations.put(board.getPiece(p), p);
        } for (Position p : board.getPositions(Colour.GREEN)) {
            this.greenPieceLocations.put(board.getPiece(p), p);
        }
    }


    /**
     * Returns a set of all availble moves for a given piece based on the working board
     * Moves are represented with the standard {start, end} position array
     * @param position representation of where the given piece is located on the board
     * @return a set containing all possible moves for a given piece
     *
     **/
    private HashSet<Position[]> getAvailableMoves(Position position) {

    Piece mover = this.workingBoard.getPiece(position);
    if (mover == null) {
        mover = null;
    }
    PieceType moverType = mover.getType();

    HashSet<Position[]> possibleMoves= new HashSet<Position[]>();
    Position p;

    for (Direction[] moves : moverType.getSteps()) { //iterate over every possible step a piece can make

        p = position;

        for (int i = 1; i <= moverType.getStepReps(); i++) { //iterate that step as many times as possible to check every possible move
            try { //try the move
                p = this.workingBoard.step(mover, moves, p, position.getColour() != p.getColour());
                if (this.workingBoard.isLegalMove(position, p)) possibleMoves.add(new Position[] {position, p}); //move is good, add it to the list
            } catch(ImpossiblePositionException e) { 
                break; //ended up in an illegal position, abandon that step and try another
            }

        }
    }

    return possibleMoves;

    }

    /**
     * Returns all possible moves a player can make based on the working board
     */
    private HashSet<Position[]> getAllAvailableMoves(Colour player) {

        HashSet<Position[]> allMoves = new HashSet<Position[]>();
        HashMap<Piece, Position> pieceLocations = this.getPlayerPieceLocations(player);

        for (Position p : pieceLocations.values()) {
            allMoves.addAll(this.getAvailableMoves(p));
        }

        return allMoves;

    }

    /**
     * Method to cleanly get a players workingPieceLocations from my clumsy representation
     * @param player the player in questions
     * @return the hashmap of their piece positions
     */
    private HashMap<Piece, Position> getPlayerPieceLocations(Colour player) {
        switch (player) {
            case RED:
                return this.redWorkingPieceLocations;
            case BLUE:
                return this.blueWorkingPieceLocations;
            case GREEN:
                return this.greenWorkingPieceLocations;      
        }
        //to make the compiler happy
        return null;
    }

    /**
     * Method that returns the kings home square for a given colour 
     * Helps check for castling moves in workingBoardMove
     * @param player specifies the side of the board
     * @return the king home position for that side
     */
    private Position getKingHome(Colour colour) {
        switch (colour) {
            case RED:
                return Position.RE1;
            case BLUE:
                return Position.BE1;
            case GREEN:
                return Position.GE1;
        }
        //to make the compiler happy
        return null;
    }

    // Miscellaneous Methods -------------------------------------------------------------------

    /**
     * Performs one-time initialisation to get everything ready for play
     * @param board the initial board to use for initialisation
     */
    private void initialiseAgent(Board board) {

        this.playerColour = board.getTurn();
        this.resetWorkingBoard(board);
        this.resetWorkingPieceLocations();
        this.kingHomePosition = this.getKingHome(this.playerColour);
        this.initGameTree();

    }

    /**
     * Method to update our representations of the game state after the opponents ply(s)
     * @param board the current game state, some number of ply ahead of our last move
     */
    private void gameStateUpdate(Board board) {

        this.resetWorkingBoard(board);
        this.resetWorkingPieceLocations();

        int m = board.getMoveCount();
        if (m < 3) {
            //We have not played a move previously, and so have nothing to update
        } else {
            //Get the three most recent moves to prune gameTree
            Position[][] moves = new Position[][] {board.getMove(m - 3), board.getMove(m-2), board.getMove(m-1)};
            this.updateGameTree(moves);
        }
        //and update the piece locations
    }


    /**
     * @return the Agent's name, for annotating game description.
     * **/ 
    public String toString(){return name;}

    /**
     * Displays the final board position to the agent, 
     * if required for learning purposes. 
     * Other a default implementation may be given.
     * @param finalBoard the end position of the board
     * **/
    public void finalBoard(Board finalBoard){}

    //Rollout Agents

    public Position[] fastRandomRollout() {
        //Position[] pieces = this.getPlayerPieceLocations(board.getTurn()).values().toArray(new Position[0]);
        Position[] pieces = this.getPlayerPieceLocations(this.workingBoard.getTurn()).values().toArray(new Position[0]);
        Position start = pieces[0];
        Position end = pieces[0]; //dummy illegal move

        while (!this.workingBoard.isLegalMove(start, end)) {
            start = pieces[random.nextInt(pieces.length)];
            Piece mover = this.workingBoard.getPiece(start);
            Direction[][] steps = mover.getType().getSteps();
            Direction[] step = steps[random.nextInt(steps.length)];
            int reps = 1 + random.nextInt(mover.getType().getStepReps());
            end = start;
            try {
            for (int i = 0; i<reps; i++)
                end = this.workingBoard.step(mover, step, end, start.getColour()!=end.getColour());
            } catch(ImpossiblePositionException e){}
        }

        return new Position[] {start,end};

    }
    
    /**
     * Class to represent the possible future game states of any three chess position
     * Each node represents a three chess position, and nodes are linked by legal moves 
     * Nodes will have some object stored in them to faciliate evaluation of positions and/or moves
     * Parameterised for flexibility, nodes will be of the type T
     * Includes functionality to replace the tree with one of its subtrees
     **/

    private class GameTree {

        private MCNode root; //Root node 
        private Colour rootPlayer; //Keeps track of whose ply it is in the root game state
        private MCNode traversalNode; //Keep track of location in tree when traversing
        private int traversalDepth; //Keeps track of how many ply deep the current traversal is

        /**
         * Basic Constructor
         * @param root the node to use as the root for the tree
         * @param player the current turn for the root game state
         **/
        public GameTree(MCNode root, Colour player) {
            this.root = root;
            this.traversalNode = root;
            this.traversalDepth = 0;
            this.rootPlayer = player;
        }

        /**
        * Tree Init Constructor
        * @param player the current turn for the root game state
        **/
    public GameTree(Colour player) {
        this.root = new MCNode(null, null);
        this.traversalNode = root;
        this.traversalDepth = 0;
        this.rootPlayer = player;
    }

        //Getters and setters -------------------------------------------------------------------------------

        public MCNode getRoot() {return this.root;}
        public MCNode getCurrentNode() {return this.traversalNode;}
        public Colour getRootPlayer() {return this.rootPlayer;}
        public int getTraversalDepth() {return this.traversalDepth;}
        public Colour getTraversalPlayer() {return Colour.values()[(this.rootPlayer.ordinal() + this.traversalDepth) % 3];}

        // Traversal -------------------------------------------------------------------------------

        public void resetTraversal() {this.traversalNode = this.root; this.traversalDepth = 0;}
        public boolean traversalAtLeaf() {return this.traversalNode.isLeaf();}
        public boolean traversalAtRoot() {return this.traversalNode == this.root;}

        /**
         * Method to traverse the tree in a single move
         * @param move the ply used to make the next step along the tree (assumed to be valid + legal)
         * @return true if traversal successfully occured, false if no such child was found
         **/
        public boolean traverse(Position[] move) {
            MCNode node = traversalNode.getChild(move);

            if (node == null) {
                return false;
            } else {
                traversalNode = node;
                this.traversalDepth++;
                return true;
            }

        }

        /**
         * Method to traverse the tree in multiple moves
         * Overloaded
         * @param move an array of ply represenattions used to make the steps along the tree ( all assumed to be valid + legal)
         * @return true if traversal successfully finished, false if it halted at any point
         **/
        public boolean traverse(Position[][] moves) {

            for (Position[] move : moves) {
                MCNode node = traversalNode.getChild(move);

                if (node == null) {
                    return false;
                } else {
                    traversalNode = node;
                    this.traversalDepth++;
                }
            }

            return true;
        }

        /**
         * Traverse back up the tree
         * @param steps the number of steps to move back up
         * @return true if the traversal succeeded, false otherwise
         */
        public boolean traverseBack(int steps) {
            while (steps > 0) {
                if (this.traversalAtRoot()) return false;
                this.traversalNode = this.traversalNode.getParent();
                this.traversalDepth--;
                steps--;
            }

            return true;

        }

        /**
         * Traverse back up the tree
         * Method overloading, single step
         * @return true if the traversal succeeded, false otherwise
         */
        public boolean traverseBack() {
            return this.traverseBack(1);
        }

        /**
         * Add a new child node to the current traversal node
         * @param move the ply leading to the game state of the new child, assumed to be valid + legal
         * If the node already exists then the expansion will fail  
         * @return true if the expansion succeeded, false otherwise
         */
        public boolean expandTraversalNode(Position[] move) {
            for (MCNode child : this.traversalNode.getChildren()) {
                if (child.getMove()[0] == move[0] && child.getMove()[1] == move[1]) {
                    return false;
                }
            }

            this.traversalNode.addChild(move);
            return true;
        }

        //MCTS Methods ----------------------------------------------------------------------------------------
        
        /**
         * Returns a child node of the current traversal node
         * The child is chosen based on its UCT score
         * @return the move linking the traversal node to the child node with the highest UCT score
         * @throws NullPointerException if the traversalNode has no children (i.e. is a leaf node)
         */
        public Position[] uctSelectChild() {

            if (this.traversalNode.isLeaf()) 
            throw new IllegalArgumentException("Traversal node is leaf, cannot get best UCT child");

            double maxUCT = 0.0;
            Position[] bestMove = null;

            for (MCNode child : this.traversalNode.getChildren()) {
                if (child.getUCT() > maxUCT) {
                    maxUCT = child.getUCT();
                    bestMove = child.getMove();
                }
            }

            return bestMove;

        }

        /**
         * Method that takes the result of a rollout and updates the current traversal node
         * Note that the traversalPlayer Colour does NOT match the Colour associated with the stats at each node
         * The Stat Colour will be the Colour prior to the traversal Colour
         * @param winner the winner of the rollout
         * @param loser the loser of the rollout
         */
        public void updateTraversal(Colour winner, Colour loser) {
            //Some sneaky math to get the Colour of the move that leads to the current traversal node
            //Also avoid a negative index if its the first move and we're at the root
            Colour traversalPlayer = Colour.values()[(3 + this.rootPlayer.ordinal() + this.traversalDepth - 1) % 3];
            if (traversalPlayer == winner) {
                this.traversalNode.rolloutUpdate(1.0);
            } else if (traversalPlayer == loser) {
                this.traversalNode.rolloutUpdate(0.0);
            } else {
                this.traversalNode.rolloutUpdate(0.5);
            }
        }

        /**
         * Return the best current move 
         * @return a child of the root node with the highest denominator
         */
        public Position[] selectMove() {

            double mostVisits = 0;
            Position[] bestMove = null;

            for (MCNode c : this.root.getChildren()) {
                if (c.getDenominator() > mostVisits) {
                    mostVisits = c.getDenominator();
                    bestMove = c.getMove();
                }
            }
            
            return bestMove;
        }

        /**
         * Updates the tree to keep up with the two most recent ply made by opponents
         * The new root will correspond to the current game state
         * @param moves the two most recent ply, oldest first
         */
        public void mctsPrune(Position[][] moves) {

            this.resetTraversal();

            for (Position[] move : moves) {
                this.traverse(move);
            }

            this.root = this.traversalNode;
            this.resetTraversal();
            this.root.setParent(null);
        }

        //Miscellaneous Methods -------------------------------------------------------------------------------

        /**
         * Class to maintain win/draw/loss statistics for Monte Carlo Tree Search
         * Will store a numerator and denominator to represent this
         * Numerator increased by: 1 for a win, 0.5 for a draw, 0 for a loss
         * Denominator increased by 1 every time a MCTS rollout uses the move associated with this node
         * Note that ALL ply (reprsented by Position[2]) are assumed to be valid and legal on the corresponding board 
         **/
        private class MCNode {

            private MCNode parent = null;
            private Position[] parentMove = new Position[2]; //move taken to traverse from the parent node to this node, assumed to be legal
            private ArrayList<MCNode> children = new ArrayList<MCNode>();
            private double numerator;
            private double denominator;
            private double uctResult; //Store the UCT formula result here and update it as needed

            /**
             * Parent-present constructor
             * Stats are set to (0/0)
             * @param parent the parent of this node, null if there isn't one
             * @param parentMove the move linking the parent node to this node
             **/
            public MCNode(MCNode parent, Position[] parentMove) {
                this.parent = parent;
                this.parentMove = parentMove;
                this.numerator = 0.0;
                this.denominator = 0.0;
                this.updateUCT();
            }

            //Getters and setters -------------------------------------------------------------------------------

            public MCNode getParent() {return this.parent;}
            public Position[] getMove() {return this.parentMove;}
            public ArrayList<MCNode> getChildren() {return this.children;}
            public double getNumerator() {return this.numerator;}
            public double getDenominator() {return this.denominator;}
            public double getFraction() {return this.numerator / this.denominator;}
            public double getUCT() {return this.uctResult;}
            public void setDenominator(double d) {this.denominator = d;}

            /**
             * Return the child node that corresponds to the threechess position that would arise after the ply described by
             * move is played on the position represented by the current node
             * @param move the ply to use to select the child
             * @param createIfNone if true, create a child node if the search yields nothing
             * @return the corresponding child node, null if the ply hasn't yet been expanded
             */
            public MCNode getChild(Position[] move, boolean createIfNone) {

                for (MCNode child : this.children) {
                    if (Arrays.equals(child.getMove(), move)) {
                        return child;
                    }
                }

                if (createIfNone) {
                    //Child node does not yet exist, create and add it
                    MCNode c = new MCNode(this, move);
                    this.children.add(c);

                    return c;
                } else {
                    return null;
                }

            }

            /**
             * Return the child node that corresponds to the threechess position that would arise after the ply described by
             * move is played on the position represented by the current node
             * Method overloading to default to non-creation behaviour
             * @param move the ply to use to select the child
             * @return the corresponding child node, null if the ply hasn't yet been expanded
             */
            public MCNode getChild(Position[] move) {

                for (MCNode child : this.children) {
                    if (Arrays.equals(child.getMove(), move)) {
                        return child;
                    }
                }

                return null;

            }

            
            /**
             * Sets the parent node for this node
             * @param parent the new parent node
             */
            public void setParent(MCNode parent) {
                this.parent = parent;
            }


            //Info about this node -------------------------------------------------------------------------------

            public boolean hasParent() {return this.parent == null;}
            public int numChildren() {return this.children.size();}
            public boolean isLeaf() {return this.children.size() == 0;}

            /**
             * Updater for MC rollouts
             * @param result enumerates the result of the relevant rollout (1 = win, 0.5 = draw, 0 = loss)
             **/
            public void rolloutUpdate(double result) {
                this.denominator++;
                this.numerator += result;
                this.updateUCT();

                for (MCNode c : this.children) {
                    c.updateUCT();
                }
            
            }

            //Miscellaneous Methods -------------------------------------------------------------------------------

            /**
             * Add an exisiting node as a child to this node
             * Note that any tree structure inherited from the new child will be maintained
             * As such, be mindful of creating loops in the tree
             * @param child the node to be added
             * @throws IllegalArgumentException if child is null 
             **/
            public void addChild(MCNode child) {
                if (child == null) throw new IllegalArgumentException("bad node sent to addChild, cannot be null");
                this.children.add(child);
                child.setParent(this);
            }

            /**
             * Add a new node as a child to this node
             * @param move the ply used to get from this node to the child node
             **/
            public void addChild(Position[] move) {
                MCNode child = new MCNode(this, move);
                this.children.add(child);
            }

            /**
             * Calculates the UCT forumla for a given node
             * @param node node to use in calculations
             * @return the result of the formula
             */
            public void updateUCT() {
                if (this.denominator == 0) this.uctResult = 100000000;
                else if (this.parent == null) return; //root node, does not need a UCT value
                else this.uctResult = this.getFraction() + Math.sqrt(2*Math.log(this.parent.getDenominator()) / this.denominator);
            }

            /**
             * Checks if two nodes are equal
             * @param node the node to compare with
             * @return true if the two nodes refer to the game state that corresponds to an identical series of ply
             */
            public boolean equals(MCNode node) {
                return (this.parent == node.getParent() && this.parentMove == node.getMove());
            }


        }

    }

}