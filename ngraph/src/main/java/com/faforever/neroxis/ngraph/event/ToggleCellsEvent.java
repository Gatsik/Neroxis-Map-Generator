package com.faforever.neroxis.ngraph.event;

import com.faforever.neroxis.ngraph.model.ICell;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public class ToggleCellsEvent extends EventObject {
    List<ICell> cells;
    boolean includeEdges;
    boolean show;
}