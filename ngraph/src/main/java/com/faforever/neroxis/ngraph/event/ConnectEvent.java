package com.faforever.neroxis.ngraph.event;

import com.faforever.neroxis.ngraph.model.ICell;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.awt.event.MouseEvent;

@EqualsAndHashCode(callSuper = true)
@Value
public class ConnectEvent extends EventObject {
    ICell cell;
    MouseEvent event;
    ICell dropTarget;
}
