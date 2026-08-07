package dev.core.game.dungeon.proceduralDungeon;

import dev.core.game.dungeon.proceduralDungeon.RoomFirstDungeonGenerator.*;
import dev.core.game.dungeon.proceduralDungeon.SimpleRandomWalkDungeonGenerator.*;

public class DungeonGenerationParameters {

    public static RoomFirstParameters roomFirstParametersBig = new RoomFirstDungeonGenerator.RoomFirstParameters(new SimpleRandomWalkParameters(100,20,false),
            20,20,200,200,3,true);
    public static RoomFirstParameters roomFirstParametersMedium = new RoomFirstDungeonGenerator.RoomFirstParameters(new SimpleRandomWalkParameters(50,10,false),
            10,10,100,100,2,true);
    public static RoomFirstParameters roomFirstParametersSmall = new RoomFirstDungeonGenerator.RoomFirstParameters(new SimpleRandomWalkParameters(25,5,false),
            5,5,50,50,1,true);

}
