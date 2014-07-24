package org.noahtye.scalator;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.layout.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.io.IOException;

enum CollapseState {
    COMPLETELY_COLLAPSED, CHILDREN_COLLAPSED, CHILDREN_EXPANDED, NO_EXPANDABLE_CHILDREN
}

public class Editor {
    public static void main(String[] args) {
        Display display = new Display();
        Shell shell = new Shell(display);
        shell.setLayout(new FillLayout());

        final Image expandImage = new Image(display, 106, 16);
        GC gc = new GC(expandImage);
        gc.setForeground(display.getSystemColor(SWT.COLOR_GRAY));
        gc.setBackground(display.getSystemColor(SWT.COLOR_GRAY));
        gc.fillRoundRectangle(0, 0, 106, 15, 5, 5);
        gc.setForeground(display.getSystemColor(SWT.COLOR_WHITE));
        gc.drawText("expand children", 2, 0, SWT.DRAW_TRANSPARENT);
        gc.dispose();

        final Image collapseImage = new Image(display, 106, 16);
        gc = new GC(collapseImage);
        gc.setForeground(display.getSystemColor(SWT.COLOR_GRAY));
        gc.setBackground(display.getSystemColor(SWT.COLOR_GRAY));
        gc.fillRoundRectangle(0, 0, 106, 15, 5, 5);
        gc.setForeground(display.getSystemColor(SWT.COLOR_WHITE));
        gc.drawText("collapse children", 2, 0, SWT.DRAW_TRANSPARENT);
        gc.dispose();

        final int IMAGE_MARGIN = 2;

        final Tree tree = generateBogusTree(shell);
        final Tree jsonTree = generateTreeFromJson(shell);

        /*
         * NOTE: MeasureItem and PaintItem are called repeatedly.
         * Therefore it is critical for performance that these methods
         * be as efficient as possible.
         */

        tree.addListener(SWT.MeasureItem, new Listener() {
                @Override
                public void handleEvent(Event event) {
                    TreeItem item = (TreeItem) event.item;
                    CollapseState collapseState = (CollapseState) item.getData();
                    switch (collapseState) {
                    case CHILDREN_COLLAPSED:
                        event.width += expandImage.getBounds().width + IMAGE_MARGIN;
                        return;
                    case CHILDREN_EXPANDED:
                        event.width += collapseImage.getBounds().width + IMAGE_MARGIN;
                        return;
                    }
                }
        });

        tree.addListener(SWT.PaintItem, new Listener() {
                @Override
                public void handleEvent(Event event) {
                    TreeItem item = (TreeItem) event.item;
                    CollapseState collapseState = (CollapseState) item.getData();
                    //System.out.println("painting " + item + " whose state is " + collapseState);
                    switch (collapseState) {
                    case CHILDREN_COLLAPSED: {
                        int x = event.x + event.width + IMAGE_MARGIN;
                        int itemHeight = tree.getItemHeight();
                        int imageHeight = expandImage.getBounds().height;
                        int y = event.y + (itemHeight - imageHeight) / 2;
                        event.gc.drawImage(expandImage, x, y);
                        return;
                    }
                    case CHILDREN_EXPANDED: {
                        int x = event.x + event.width + IMAGE_MARGIN;
                        int itemHeight = tree.getItemHeight();
                        int imageHeight = collapseImage.getBounds().height;
                        int y = event.y + (itemHeight - imageHeight) / 2;
                        event.gc.drawImage(collapseImage, x, y);
                        return;
                    }
                    }
                }
        });

        // tree.addListener(SWT.MouseDown, new Listener() {
        //         @Override
        //         public void handleEvent(Event event) {
        //             tree.setSelection(new TreeItem[0]);
        //         }
        //     });

        tree.addListener(SWT.MouseUp, new Listener() {
                @Override
                public void handleEvent(Event event) {
                    Point point = new Point(event.x, event.y);
                    TreeItem item = tree.getItem(point);
                    if (item == null) return;
                    CollapseState collapseState = (CollapseState) item.getData();
                    switch (collapseState) {
                    case COMPLETELY_COLLAPSED:
                        return;
                    case CHILDREN_COLLAPSED: {
                        final Rectangle itemBounds = item.getBounds();
                        final int leftBound = itemBounds.width + itemBounds.x + IMAGE_MARGIN;
                        final int rightBound = leftBound + expandImage.getBounds().width;
                        if (event.x > leftBound && event.x < rightBound) {
                            for (TreeItem childItem : item.getItems()) {
                                childItem.setExpanded(true);
                                System.out.println("  for item " + item + " children.size = " + childItem.getItems().length);
                                setCollapseState(childItem);
                            }
                            item.setExpanded(true);
                            setCollapseState(item);
                            item.getParent().redraw(itemBounds.x, itemBounds.y, itemBounds.width, itemBounds.height, false);
                            System.out.println("clicked expand.");
                        }
                        return;
                    }
                    case CHILDREN_EXPANDED: {
                        final Rectangle itemBounds = item.getBounds();
                        final int leftBound = itemBounds.width + itemBounds.x + IMAGE_MARGIN;
                        final int rightBound = leftBound + collapseImage.getBounds().width;
                        if (event.x > leftBound && event.y < rightBound) {
                            for (TreeItem childItem : item.getItems()) {
                                childItem.setExpanded(false);
                                setCollapseState(childItem);
                            }
                            item.setExpanded(true);
                            setCollapseState(item);
                            item.getParent().redraw(itemBounds.x, itemBounds.y, itemBounds.width, itemBounds.height, false);
                            System.out.println("clicked collapse.");
                        }
                    }
                    }
                }
            });

        tree.addListener(SWT.Expand, new Listener() {
                @Override
                public void handleEvent(Event event) {
                    TreeItem item = (TreeItem) event.item;
                    if (item == null) return;
                    System.out.println("expanded " + item);
                    item.setExpanded(true); // SWT apparently doesn't update this until later -_-
                    setCollapseState(item);
                    final TreeItem parent = item.getParentItem();
                    if (parent != null) parent.setData(CollapseState.CHILDREN_EXPANDED);
                    final Rectangle bounds = item.getBounds();
                    item.getParent().redraw(bounds.x, bounds.y, bounds.width, bounds.height, false);
                }
        });

        tree.addListener(SWT.Collapse, new Listener() {
            @Override
            public void handleEvent(Event event) {
                TreeItem item = (TreeItem) event.item;
                item.setData(CollapseState.COMPLETELY_COLLAPSED);
                item.setExpanded(false); // SWT apparently doesn't update this until later -_-
                setCollapseState(item.getParentItem());
                final TreeItem parent = item.getParentItem();
                if (parent != null) System.out.println("parent " + parent + " is now " + parent.getData());
                final Rectangle bounds = item.getBounds();
                item.getParent().redraw(bounds.x, bounds.y, bounds.width, bounds.height, false);
            }
        });

        shell.setSize(300, 200);
        shell.open();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) display.sleep();
        }
        display.dispose();
    }

    private static void setCollapseState(TreeItem item) {
        if (item == null) {
            return;
        } else if (!item.getExpanded()) {
            item.setData(CollapseState.COMPLETELY_COLLAPSED);
        } else {
            boolean hasGrandchildren = false;
            for (TreeItem child : item.getItems()) {
                if (child.getItems().length > 0) hasGrandchildren = true;
            }
            if (!hasGrandchildren) {
                item.setData(CollapseState.NO_EXPANDABLE_CHILDREN);
                return;
            }
            for (TreeItem child : item.getItems()) {
                if (child.getExpanded()) {
                    // at least one child is expanded
                    item.setData(CollapseState.CHILDREN_EXPANDED);
                    return;
                }
            }
            // not a single child is expanded
            item.setData(CollapseState.CHILDREN_COLLAPSED);
        }
    }

    private static Tree generateBogusTree(Shell shell) {
        final Tree tree = new Tree(shell, 0);
        for (int i=0; i<4; i++) {
            TreeItem iItem = new TreeItem(tree, 0);
            iItem.setText("TreeItem (0) - " + i);
            iItem.setData(CollapseState.COMPLETELY_COLLAPSED);
            for (int j=0; j<4; j++) {
                TreeItem jItem = new TreeItem(iItem, 0);
                jItem.setText("TreeItem (1) - " + j);
                jItem.setData(CollapseState.COMPLETELY_COLLAPSED);
                for (int k=0; k<4; k++) {
                    TreeItem kItem = new TreeItem(jItem, 0);
                    kItem.setText("TreeItem (2) - " + k);
                    kItem.setData(CollapseState.COMPLETELY_COLLAPSED);
                }
            }
        }
        return tree;
    }

    private static Tree generateTreeFromJson(Shell shell) {
        final Tree tree = new Tree(shell, 0);
        try {
            ObjectMapper mapper = new ObjectMapper();
            // TODO relative path?
            JsonNode rootNode = mapper.readValue(new File("/Users/noah/waldzell/example.json"), JsonNode.class);
            System.out.println(rootNode);
            System.out.println(rootNode.fieldNames());
        } catch (IOException e) {
            System.out.println("some io exception :(");
        }
        return tree;
    }
}

