package dyatel.terracontrol.level;

import dyatel.terracontrol.network.Player;
import dyatel.terracontrol.util.Util;

import java.util.ArrayList;
import java.util.Random;

public class CellMaster implements Updatable {

    private int color; // Color of master

    private int id; // ID

    private ArrayList<Cell> newCells = new ArrayList<Cell>(); // Cells that we need to main list
    private ArrayList<Cell> cells = new ArrayList<Cell>(); // Main cell list
    private ArrayList<Cell> borderCells = new ArrayList<Cell>(); // Cells that touch different masters

    private ArrayList<CellMaster> neighbors = new ArrayList<CellMaster>(); // Masters touching our border

    private Player owner = null; // Player that controls us

    private Level level; // Level

    private boolean removed = false; // Are we removed from level

    private Random random = Util.getRandom(); // Random

    public CellMaster(Level level) {
        init(random.nextInt(level.getColors().length), level);
    }

    public CellMaster(int colorID, Level level) {
        init(colorID, level);
    }

    private void init(int colorID, Level level) {
        color = colorID;
        this.level = level;

        level.add(this);
    }

    private void tryToAdd(int x, int y) {
        if (level.canSetCell(x, y)) new Cell(x, y, this);
    }

    private void merge(CellMaster master) {
        // Updating cell lists and setting new master to each cell
        cells.addAll(newCells); // We must not ignore new cells if we haven`t been updated
        for (Cell cell : cells) cell.setMaster(master);

        // Just giving cells, cause we`ll be deleted and other master already has right color and owner
        master.addCells(cells);

        remove(); // Removing ourselves because we merged with other master
    }

    private void checkNeighbor(int x, int y) {
        if (level.getCell(x, y) != null) {
            CellMaster neighbor = level.getMaster(x, y);
            if (neighbor != this && neighbor.getColorID() == color && neighbor.getOwner() == null) {
                neighbor.merge(this); // Merging with neighbor if needed
            } else {
                if (!neighbors.contains(neighbor) && neighbor != this) neighbors.add(neighbor);
            }
        }
    }

    public void generate() {
        for (Cell cell : borderCells) {
            // Determining where to try putting new cell
            int x = cell.getX();
            int y = cell.getY();
            switch (random.nextInt(4)) {
                case 0:
                    x -= 1;
                    break;
                case 1:
                    y -= 1;
                    break;
                case 2:
                    x += 1;
                    break;
                case 3:
                    y += 1;
                    break;
            }
            tryToAdd(x, y);
        }
    }

    public void update() {
        // Updating while updates change something
        do {
            // Adding all new cells
            cells.addAll(newCells);
            newCells.clear();

            // Recalculating borders
            neighbors.clear();
            borderCells.clear();
            for (Cell cell : cells) {
                // If at least one cell contacts with other master (or no master) then it`s placed on border
                if (level.getMaster(cell.getX() - 1, cell.getY()) != this ||
                        level.getMaster(cell.getX(), cell.getY() - 1) != this ||
                        level.getMaster(cell.getX() + 1, cell.getY()) != this ||
                        level.getMaster(cell.getX(), cell.getY() + 1) != this) {
                    borderCells.add(cell); // Need this to generate level

                    // Finding neighbors
                    checkNeighbor(cell.getX(), cell.getY() - 1);
                    checkNeighbor(cell.getX() + 1, cell.getY());
                    checkNeighbor(cell.getX(), cell.getY() + 1);
                    checkNeighbor(cell.getX() - 1, cell.getY());
                }
            }
        } while (newCells.size() > 0); // We should update at least one time to find neighbors
    }

    public void setColorID(int color) {
        this.color = color;
    }

    public int getColorID() {
        return color;
    }

    public void setID(int id) {
        this.id = id;
    }

    public int getID() {
        return id;
    }

    public void setOwner(Player owner) {
        this.owner = owner;
    }

    public Player getOwner() {
        return owner;
    }

    public void addCells(ArrayList<Cell> cells) {
        newCells.addAll(cells);
        level.needUpdate(this);
    }

    public void addCell(Cell cell) {
        newCells.add(cell);
        level.needUpdate(this);
    }

    public ArrayList<Cell> getCells() {
        return cells;
    }

    public boolean isNeighbor(CellMaster master) {
        return neighbors.contains(master);
    }

    public ArrayList<CellMaster> getNeighbors() {
        return neighbors;
    }

    public Level getLevel() {
        return level;
    }

    public void remove() {
        removed = true;
    }

    public boolean isRemoved() {
        return removed;
    }

}
