package env.model;

import java.util.*;

import utils.Utils;

public class AquariumModelImpl implements AquariumModel {
    private static final int FOOD_OFFSET = 7;
    private final Map<String, Fish> agents = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, Food> food = Collections.synchronizedMap(new HashMap<>());
    private final Set<Fish> recentEaters = Collections.synchronizedSet(new HashSet<>());
    private final List<Pair<String, String>> events = Collections.synchronizedList(new ArrayList<>());
    private List<Obstacle> obstacles;
    private int width;
    private int height;
    private long foodId;
    private int totalNumberOfFoodEaten;
    private int foodQuantity;

    public AquariumModelImpl() {
        this.obstacles = Collections.synchronizedList(new ArrayList<>());
        this.foodId = 0;
        this.totalNumberOfFoodEaten = 0;
    }

    @Override
    public void setAquariumDimensions(int width, int height){
        this.width = width;
        this.height = height;
    }

    @Override
    public boolean containsAgent(String name) {
        synchronized(this.agents){
            return agents.containsKey(name);
        }
    }

    @Override
    public Set<Fish> getAllAgents() {
        synchronized(this.agents){
            return new HashSet<>(agents.values());
        }
    }

    @Override
    public void removeAgent(String name){
        synchronized(this.agents){
            this.ensureAgentExists(name);
            this.agents.remove(name);        
            this.addEventToList(new Pair<>(name, "die"));
        }
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public boolean isPositionInside(double x, double y) {
        return x >= 0 && x < width && y >= 0 && y < height ;
    }

    private void ensureAgentExists(String agent) {
        if (!containsAgent(agent)) {
            throw new IllegalArgumentException("No such an agent: " + agent);
        }
    }

    @Override
    public Set<Food> getAllFood() {
        synchronized(this.food){
            return new HashSet<>(this.food.values());
        }
    }

    @Override
    public Fish getAgent(String agent) {
        synchronized(this.agents){
            this.ensureAgentExists(agent);
            return this.agents.get(agent);
        }
    }

    @Override
    public boolean isAgentCloseToFood(String agent, String food) {
        synchronized(this){
            this.ensureAgentExists(agent);
            Food f = this.food.get(food);
            if(f == null){
                return false;
            }
            Position foodPos = f.getPosition();
            Fish fish = this.agents.get(agent);
            Vector2D dir = Vector2D.fromPositions(fish.getPosition(), foodPos);
            return dir.getLength() <= fish.getRange();
        }
    }

    @Override
    public boolean isFoodWithinObstacle(String food) {
        synchronized(this){
            Food f = this.food.get(food);
            if(f == null){
                return false;
            }
            Position foodPos = f.getPosition();
            return this.obstacles.stream().anyMatch(o -> Vector2D.fromPositions(foodPos, o.getPosition()).getLength() < o.getRadius());
        }
    }

    @Override
    public void moveTowards(String agent, double x, double y, Speed speed) {
        synchronized(this.agents){
            this.ensureAgentExists(agent);
            Fish fish = this.agents.get(agent);
            Position pos = fish.getPosition();
            pos.addX(x);
            pos.addY(y);
            fish.moveTowards(pos, speed);
        }
            
    }

    @Override
    public void sink(String food) {
        synchronized(this.food){
            if(!this.food.containsKey(food)){
                throw new IllegalArgumentException("No such food: " + food);
            }

            this.food.get(food).sink(); 
        }
    }

    @Override
    public Optional<Food> getFoodByPosition(double x, double y) {
        synchronized(this.food){
            return this.food.values().stream()
                                        .filter(f -> f.getPosition().getX() == x && f.getPosition().getY() == y)
                                        .findFirst();
        }
    }

    @Override
    public Set<Obstacle> getAllObstacles() {
        return new HashSet<>(this.obstacles);
    }

    @Override
    public boolean isAgentCloseToObstacle(String agent, Obstacle obstacle) {
        synchronized(this.agents){
            this.ensureAgentExists(agent);
            Position obstaclePos = new Position(obstacle.getX(), obstacle.getY());
            Fish fish = this.agents.get(agent);
            Vector2D dir = Vector2D.fromPositions(fish.getPosition(), obstaclePos);
            return dir.getLength() <= fish.getSize() / 2 + fish.getObstacleRange() + obstacle.getRadius();
        }
    }

    @Override
    public boolean eat(String agent, String foodId) {
        synchronized(this){
            this.ensureAgentExists(agent);
            if (!this.food.containsKey(foodId)) {
                return false;
            }
            if (!this.isAgentCloseToFood(agent, foodId)) {
                return false;
            }
            Fish fish = this.agents.get(agent);
            this.addEventToList(new Pair<>(agent, "eat"));
            fish.addEnergy(Utils.FOOD_ENERGY_INCREASE);
            this.food.remove(foodId);
            if(!this.recentEaters.add(fish)){
                this.recentEaters.remove(fish);
                this.recentEaters.add(fish);
            }
            this.totalNumberOfFoodEaten++;
            fish.incrementFoodEaten();
            this.addEventToList(new Pair<>(agent, "digest"));
            return true;
        }
    }

    @Override
    public boolean canAgentEatFood(String agent, String foodId) {
        synchronized(this){
            this.ensureAgentExists(agent);
            if (!this.food.containsKey(foodId)) {
                return false;
            }
            Fish fish = this.agents.get(agent);
            Food food = this.food.get(foodId);
    
            Vector2D dir = Vector2D.fromPositions(fish.getPosition(), food.getPosition());
            return dir.getLength() <= fish.getEatingRange() + fish.getSize() / 2;
        }
    }

    @Override
    public void addFish(String agentName, double weight, double energy, double maxEnergy, Position position) {
        synchronized(this.agents){
            this.addEventToList(new Pair<>(agentName, "add"));
            this.agents.put(agentName, new Fish(agentName, weight, energy, maxEnergy, position));
        }
    }

    @Override
    public void addFood(Position position) {
        synchronized(this.food){
            String id = "food" + this.foodId++;
            this.food.put(id, new Food(id, position, height - FOOD_OFFSET));
            if(foodId < 0){
                foodId = 0;
            }
        }
    }

    @Override
    public void addObstacle(Position position, double radius) {
        this.obstacles.add(new Obstacle(position.getX(), position.getY(), radius));
    }

    @Override
    public boolean isAgentCloseToBorder(String agent, Direction dir) {
        synchronized(this.agents){
            this.ensureAgentExists(agent);
            Fish fish = this.agents.get(agent);
            Position fishPosition = fish.getPosition();
            switch (dir) {
                case LEFT:
                    return fishPosition.getX() <= fish.getObstacleRange() + fish.getSize() / 2;
                case RIGHT:
                    return fishPosition.getX() >= this.width - fish.getObstacleRange() - fish.getSize() / 2;
                case TOP:
                    return fishPosition.getY() <= fish.getObstacleRange() + fish.getSize() / 2;
                default:
                    return fishPosition.getY() >= this.height - fish.getObstacleRange() - fish.getSize() / 2;   
            }
        }
    }

    @Override
    public int getNumberOfFoodEaten() {
        return this.totalNumberOfFoodEaten;
    }

    @Override
    public int getFoodQuantity() {
        return this.foodQuantity;
    }

    @Override
    public void setFoodQuantity(int amount) {
        this.foodQuantity = amount;
    }

    @Override
    public boolean isAgentCloseToOtherAgent(String agent1, String agent2) {
        synchronized(this.agents){
            Fish fish1 = this.agents.get(agent1);
            Fish fish2 = this.agents.get(agent2);
    
            ensureAgentExists(agent1);
            if (fish2 == null) {
                return false;
            }

            if(agent1 == agent2){
                return true;
            }
            
            Vector2D dir = Vector2D.fromPositions(fish1.getPosition(), fish2.getPosition());
            return dir.getLength() <= fish1.getSize() / 2 + fish1.getObstacleRange() + fish2.getSize() / 2;
        }
    }

    @Override
    public double getFairnessIndex() {
        synchronized(this.agents){
            if(this.agents.size() < 2){
                return Double.NaN;
            }
            long maxNumberOfFoodEaten = this.agents.values().stream().mapToLong(f -> f.getNumberOfFoodEaten()).max().getAsLong();
            if(maxNumberOfFoodEaten == 0){
                return 1;
            }
            var mean = 1.0 * this.totalNumberOfFoodEaten / this.agents.size();
            var variance = this.agents.values().stream()
                .mapToDouble(f -> Math.pow(f.getNumberOfFoodEaten() - mean, 2))
                .sum() / (this.agents.size() - 1);
            return Math.exp(-variance / (3 * maxNumberOfFoodEaten));
        }
    }

    @Override
    public String verifyEvents() {
        synchronized (this.events) {
            Map<String, List<String>> groupedEvents = new HashMap<>();
            for (Pair<String, String> event : events) {
                groupedEvents.computeIfAbsent(event.getFirst(), k -> new ArrayList<>()).add(event.getSecond());
            }

            for (Map.Entry<String, List<String>> entry : groupedEvents.entrySet()) {
                List<String> fishEvents = entry.getValue();
                if(!fishEvents.get(0).equals("add")){
                    return "first event was not init";
                }

                if(!fishEvents.get(fishEvents.size() - 1).equals("die")){
                    return "last event was not die";
                }
                
                for (int i = 0; i < fishEvents.size() - 1; i++) {
                    if (fishEvents.get(i).equals("eat")) {
                        if(!fishEvents.get(i + 1).equals("digest")){
                            return "a digest event does not follow an eat event";
                        }
                    }
                }

                for (int i = 1; i < fishEvents.size(); i++) {
                    if (fishEvents.get(i).equals("eat")) {
                        if(!fishEvents.get(i - 1).equals("food_percept")){
                            return "a food_percept event does not precede an eat event";
                        }
                    }
                }
            }
        }
        return "";
    }

    @Override
    public void addEventToList(Pair<String, String> event) {
        synchronized (this.events) {
            this.events.add(event);
        }
    }
}
