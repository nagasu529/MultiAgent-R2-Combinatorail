package Agent;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import Agent.CropTest.cropType;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import java.text.DecimalFormat;
import java.util.*;

public class CombinatorialBidder extends Agent {
    //The list of farmer who are seller (maps the water volumn to its based price)
    CropTest calCrops = new CropTest();

    DecimalFormat df = new DecimalFormat("#.##");

    //Farmer information on each agent.
    agentInfo farmerInfo = new agentInfo("", "", 0.0,0.0, 0.0, "", 0.0, 0.0, 0);

    //Global bidding parameter

    // The GUI by means of which the user can add books in the catalogue
    private CombinatorialBidderGUI myGUI;


    protected void setup() {
        System.out.println(getAID().getName()+"  is ready" );

        // Create and show the GUI
        myGUI = new CombinatorialBidderGUI(this);
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
                    myGUI.displayUI("Total buying water needed: " + df.format(farmerInfo.buyingVolumn) + "\n");
                    myGUI.displayUI("Water need currently " + df.format(farmerInfo.currentLookingVolumn) + "\n");
                    myGUI.displayUI("Maximum buying price (per MM.) " + df.format(farmerInfo.buyingPricePerMM) + "\n");
                    myGUI.displayUI("Selling / Buying stages " + farmerInfo.sellingStatus + "\n");
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
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);
            String log = new String();
            //CFP Message received. Process it.
            if (msg != null) {
                ACLMessage reply = msg.createReply();

                //Price Per MM. and the number of volumn to sell from Seller.
                String currentOffer = msg.getContent();
                String[] arrOfstr = currentOffer.split("-");
                myGUI.displayUI("Offer from Seller: " + currentOffer + "\n");
                farmerInfo.waterVolumnFromSeller = Double.parseDouble(arrOfstr[0]);
                farmerInfo.waterPriceFromSeller = Double.parseDouble(arrOfstr[1]);
                //farmerInfo.numBidder = Integer.parseInt(arrOfstr[2]);

                myGUI.displayUI("Price setting up from Seller: " + farmerInfo.waterPriceFromSeller + " per MM" + "\n");
                myGUI.displayUI("Selling volume from seller:" + farmerInfo.waterVolumnFromSeller + "\n");
                myGUI.displayUI("Number of bidder :" + farmerInfo.numBidder + "\n");

                //Auction Process
                if (farmerInfo.waterPriceFromSeller <= farmerInfo.buyingPricePerMM) {
                    reply.setPerformative(ACLMessage.PROPOSE);
                    //String sendingOffer = farmerInfo.farmerName + "-" + farmerInfo.buyingVolumn + "-" + farmerInfo.buyingPricePerMM;
                    String sendingOffer = farmerInfo.buyingVolumn + "-" + farmerInfo.buyingPricePerMM;
                    reply.setContent(sendingOffer);
                    myAgent.send(reply);
                    myGUI.displayUI("Sending Offer : " + reply.getContent() + "\n");
                    myGUI.displayUI(log + "\n");
                } else {
                    reply.setPerformative(ACLMessage.REFUSE);
                    //reply.setContent(getAID().getName() + " is surrender");
                    myAgent.send(reply);
                    myGUI.displayUI(getAID().getName() + " is surrender");
                }
            } else {
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
                farmerInfo.currentLookingVolumn = farmerInfo.buyingVolumn;
                farmerInfo.currentLookingVolumn = farmerInfo.buyingVolumn - farmerInfo.currentLookingVolumn;

                if (farmerInfo.currentLookingVolumn <=0) {
                    farmerInfo.sellingStatus = "Finished bidding";
                    myAgent.send(reply);

                    //Delete service and deregister service from the system.
                    myAgent.doDelete();
                    myGUI.dispose();
                    System.out.println(getAID().getName() + " terminating.");
                }
                myAgent.send(reply);
            }else {
                block();
            }
        }
    }

    public void bidderInput(final Double buyingPrice, Double volumnToBuy){

        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                //input bidding information parameter
                farmerInfo.sellingStatus = "looking";
                farmerInfo.buyingPricePerMM = buyingPrice;
                farmerInfo.buyingVolumn = volumnToBuy;
            }
        });
    }

    public class agentInfo{
        String farmerName;
        String agentType;
        double buyingVolumn;
        double currentLookingVolumn;
        double buyingPricePerMM;
        String sellingStatus;
        double waterVolumnFromSeller;
        double waterPriceFromSeller;
        double numBidder;

        agentInfo(String farmerName, String agentType, double buyingVolumn, double currentLookingVolumn,
                  double buyingPricePerMM, String sellingStatus, double waterVolumnFromSeller, double waterPriceFromSeller, double numBidder){
            this.farmerName = farmerName;
            this.agentType = agentType;
            this.buyingVolumn = buyingVolumn;
            this.currentLookingVolumn = currentLookingVolumn;
            this.buyingPricePerMM = buyingPricePerMM;
            this.sellingStatus = sellingStatus;
            this.waterVolumnFromSeller = waterVolumnFromSeller;
            this.waterPriceFromSeller = waterPriceFromSeller;
            this.numBidder = numBidder;
        }
    }
}
