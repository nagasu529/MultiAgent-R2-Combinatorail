package Agent;

import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import Agent.Crop.cropType;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import java.text.DecimalFormat;


public class Bidder extends Agent {

    //The list of farmer who are seller (maps the water volumn to its based price)
    Crop calCrops = new Crop();

    DecimalFormat df = new DecimalFormat("#.##");

    //Farmer information on each agent.
    agentInfo farmerInfo = new agentInfo("", "", 0.0, 0.0, 0.0, 0.0, "Bidded", 0.0,
            0.0, 0.0, 0.0, 0.0, 0);

    //Global bidding parameter
    Double increasingBidRate;

    // The GUI by means of which the user can add books in the catalogue
    private BidderGUI myGUI;


    protected void setup() {
        System.out.println(getAID().getName()+"  is ready" );

        // Create and show the GUI
        myGUI = new BidderGUI(this);
        myGUI.show();

        //Start Agent
        // Register the book-selling service in the yellow pages
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        farmerInfo.agentType = "Farmer-auctioneer";
        sd.setType("bidder");
        //sd.setType(farmerInfo.agentType);
        sd.setName(getAID().getName());
        farmerInfo.farmerName = getAID().getName();
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
        //Bidding process.
        addBehaviour(new TickerBehaviour(this, 10000) {
            public void onTick() {

                if (farmerInfo.sellingStatus=="looking"){
                    myGUI.displayUI("\n");
                    myGUI.displayUI("Name: " + farmerInfo.farmerName + "\n");
                    myGUI.displayUI("Status: " + farmerInfo.agentType + "\n");
                    myGUI.displayUI("Total buying water needed: " + df.format(farmerInfo.currentLookingVolumn));
                    myGUI.displayUI("The target volume for buying : " + df.format(farmerInfo.currentLookingVolumn - farmerInfo.currentBidVolumn) + "\n");
                    myGUI.displayUI("Bidding price: " + df.format(farmerInfo.pricePerMM) + "\n");
                    myGUI.displayUI("Bidding status: " + farmerInfo.sellingStatus + "\n");
                    myGUI.displayUI("MaxPrice:" + farmerInfo.maxPricePerMM + "\n");
                    myGUI.displayUI("Number of bidder :" + farmerInfo.numBidder + "\n");
                    myGUI.displayUI("\n");

                    /*
                     ** Bidding water process
                     */
                    //Add the behaviour serving queries from Water provider about current price.
                    addBehaviour(new OfferRequestsServer());

                    //Add the behaviour serving purhase orders from water provider agent.
                    addBehaviour(new PurchaseOrdersServer());
                }
                else {
                    myGUI.displayUI("Do not want to bid water this time" + "\n");
                }
            }
        });
    }

    // Put agent clean-up operations here
    protected void takeDown() {
        // Deregister from the yellow pages
        try {
            DFService.deregister(this);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
        // Close the GUI
        myGUI.dispose();
        // Printout a dismissal message
        System.out.println("Seller-agent "+getAID().getName()+" terminating.");
    }

    private class OfferRequestsServer extends CyclicBehaviour {
        Decision auctRules = new Decision();
        private int step = 0;
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);
            String log = new String();
            //CFP Message received. Process it.
            if(msg != null){
                ACLMessage reply = msg.createReply();

                //Current price Per MM. and the number of volumn to sell.
                String currentOffer = msg.getContent();
                String[] arrOfstr = currentOffer.split("-");
                myGUI.displayUI("current Offer from Seller: " + currentOffer + "\n");
                farmerInfo.waterVolumn = Double.parseDouble(arrOfstr[0]);
                farmerInfo.currentPricePerMM = Double.parseDouble(arrOfstr[1]);
                farmerInfo.numBidder = Integer.parseInt(arrOfstr[2]);

                myGUI.displayUI("current price bidded: " + farmerInfo.currentPricePerMM + "\n");
                myGUI.displayUI("water volume from seller:" + farmerInfo.waterVolumn + "\n");
                myGUI.displayUI("Previous price: "+ farmerInfo.previousPrice + "\n");
                myGUI.displayUI("Number of bidder :" + farmerInfo.numBidder + "\n");

                //English Auction Process
                if (farmerInfo.currentPricePerMM < farmerInfo.maxPricePerMM) {
                    farmerInfo.bidedPrice = auctRules.changedPriceRate("inc", increasingBidRate,farmerInfo.currentPricePerMM);
                    String currentBidOffer;
                    if (farmerInfo.bidedPrice < farmerInfo.maxPricePerMM){
                        reply.setPerformative(ACLMessage.PROPOSE);
                        if(farmerInfo.currentPricePerMM <= farmerInfo.previousPrice && farmerInfo.numBidder ==1){
                            currentBidOffer = farmerInfo.waterVolumn + "-" + farmerInfo.previousPrice;
                        }else {
                            farmerInfo.previousPrice = farmerInfo.bidedPrice;
                            currentBidOffer = farmerInfo.waterVolumn + "-" + farmerInfo.bidedPrice;
                        }
                        reply.setContent(currentBidOffer);
                        myAgent.send(reply);
                        myGUI.displayUI("Current Offer : " + reply.getContent() + "\n");
                        myGUI.displayUI(log + "\n");
                    }
                }else {
                    reply.setPerformative(ACLMessage.REFUSE);
                    //reply.setContent(getAID().getName() + " is surrender");
                    myAgent.send(reply);
                    myGUI.displayUI(getAID().getName() + " is surrender");
                }
            }else {
                block();
            }
        }
    }

    private class PurchaseOrdersServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                // ACCEPT_PROPOSAL Message received. Process it
                ACLMessage reply = msg.createReply();
                myGUI.displayUI(msg.toString());
                System.out.println(farmerInfo.sellingStatus);
                reply.setPerformative(ACLMessage.INFORM);
                
                //water requirement for next round bidding.
                myGUI.displayUI(msg.getSender().getLocalName()+" sell water to agent "+ getAID().getLocalName());
                farmerInfo.currentBidVolumn = farmerInfo.waterVolumn;
                farmerInfo.currentLookingVolumn = farmerInfo.currentLookingVolumn - farmerInfo.currentBidVolumn;
                
                if (farmerInfo.currentLookingVolumn <=0) {
                	farmerInfo.sellingStatus = "Finished bidding";
                	myAgent.send(reply);

                	//Delete service and deregister service from the system.
                	myAgent.doDelete();
                	myGUI.dispose();
                	System.out.println(getAID().getName() + " terminating.");
				}
                
                myAgent.send(reply);
                //doSuspend();
                
                
                /*
                if (farmerInfo.sellingStatus=="looking") {
                    farmerInfo.sellingStatus = "Finished bidding";
                    //System.out.println(getAID().getName()+" sold water to agent "+msg.getSender().getName());
                    myGUI.displayUI(msg.getSender().getLocalName()+" sell water to agent "+ getAID().getLocalName());
                    //myGui.displayUI(farmerInfo.sellingStatus.toString());
                    //System.out.println(farmerInfo.sellingStatus);
                    //doSuspend();
                } else {
                    // The requested book has been sold to another buyer in the meanwhile .
                    reply.setPerformative(ACLMessage.FAILURE);
                    reply.setContent("not-available for sale");
                }*/
                

            }else {
                block();
            }
        }
    }

    public void bidderInput(final Double minPrice, Double maxPrice, Double increasePricePercentage, Double VolumnToBuy){

        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                //input bidding information parameter
                farmerInfo.sellingStatus = "looking";
                farmerInfo.minPricePerMM = minPrice;
                farmerInfo.maxPricePerMM = maxPrice;
                increasingBidRate = increasePricePercentage;
                farmerInfo.currentLookingVolumn = VolumnToBuy;
                //farmerInfo.currentBidVolumn = VolumnToBuy;
            }
        });
    }

    public class agentInfo{
        String farmerName;
        String agentType;
        double waterVolumn;
        double currentLookingVolumn;
        double currentBidVolumn;
        double pricePerMM;
        String sellingStatus;
        double minPricePerMM;
        double maxPricePerMM;
        double currentPricePerMM;
        double bidedPrice;
        double previousPrice;
        int numBidder;

        agentInfo(String farmerName, String agentType, double waterVolumn, double currentLookingVolumn, double currentBidVolumn, double pricePerMM, String sellingStatus, double minPricePerMM, double maxPricePerMM,
                  double currentPricePerMM, double biddedPrice, double previousPrice, int numBidder){
            this.farmerName = farmerName;
            this.agentType = agentType;
            this.waterVolumn = waterVolumn;
            this.currentLookingVolumn = currentLookingVolumn;
            this.currentBidVolumn = currentBidVolumn;
            this.pricePerMM = pricePerMM;
            this.sellingStatus = sellingStatus;
            this.minPricePerMM = minPricePerMM;
            this.maxPricePerMM = maxPricePerMM;
            this.currentPricePerMM = currentPricePerMM;
            this.bidedPrice = biddedPrice;
            this.previousPrice = previousPrice;
            this.numBidder = numBidder;
        }
    }
}