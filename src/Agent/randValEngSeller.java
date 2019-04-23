package Agent;

import Agent.Crop.cropType;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.text.DecimalFormat;

public class randValEngSeller extends Agent {
    randValEngSellerGUI myGui;

    //General parameter information.
    DecimalFormat df = new DecimalFormat("#.##");

    //The list of known water selling agent
    private AID[] bidderAgent;

    //Counting list (single negotiation process)
    int countTick;

    randValue randValue = new randValue();
    agentInfo farmerInfo = new agentInfo(randValue.getRandElementString(randValue.farmerNameGen),"",0,0, randValue.getRandDoubleRange(10,12),
            randValue.getRandDoubleRange(1500,2000),"", randValue.getRandDoubleRange(10,12),0,0,0,0,0);

    protected void setup(){
        System.out.println(getAID()+" is ready");

        //Creating catalogue and running GUI
        myGui = new randValEngSellerGUI(this);
        myGui.show();
        //Start agent

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        farmerInfo.agentType = "Farmer";
        ServiceDescription sd = new ServiceDescription();
        sd.setType(farmerInfo.agentType);
        sd.setName(getAID().getName());
        farmerInfo.farmerName = getAID().getName();
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        myGui.displayUI("Hello "+ farmerInfo.farmerName + "\n" + "Stage is " + farmerInfo.agentType + "\n");

        //Add a TickerBehaviour that chooses agent status to buyer or seller.
        addBehaviour(new TickerBehaviour(this, 5000){
            protected void onTick() {

                myGui.displayUI("Agent status is " + farmerInfo.agentType + "\n");
                if (farmerInfo.agentType=="owner"||farmerInfo.agentType=="Farmer-owner") {
                    //Register the seller description service on yellow pages.
                    farmerInfo.agentType = "Farmer-owner";
                    //farmerInfo.pricePerMM = 10;
                    sd.setType(farmerInfo.agentType);
                    sd.setName(getAID().getName());
                    farmerInfo.farmerName = getAID().getName();
                    farmerInfo.minPricePerMM = farmerInfo.pricePerMM;

                    myGui.displayUI("\n");
                    myGui.displayUI("Name: " + farmerInfo.farmerName + "\n");
                    myGui.displayUI("Status: " + farmerInfo.agentType + "\n");
                    myGui.displayUI("Volumn to sell: " + farmerInfo.waterVolumn + "\n");
                    myGui.displayUI("Selling price: " + farmerInfo.pricePerMM + "\n");
                    myGui.displayUI("Selling status: " + farmerInfo.sellingStatus + "\n");
                    myGui.displayUI("Maximum bidding: " + farmerInfo.maxPricePerMM + "\n");
                    myGui.displayUI("Providing price" + "\n");
                    myGui.displayUI("\n");

                    /*
                     ** Selling water process
                     */
                    addBehaviour(new RequestPerformer());
                    // Add the behaviour serving purchase orders from buyer agents
                    //addBehaviour(new PurchaseOrdersServer());
                }
            }
        } );
    }

    private class RequestPerformer extends Behaviour {
        private AID bestBidder; // The agent who provides the best offer
        private double bestPrice;  // The best offered price
        private int repliesCnt; // The counter of replies from seller agents
        private MessageTemplate mt; // The template to receive replies
        double waterVolFromBidder;
        double biddedPriceFromBidder;
        int proposeCnt, refuseCnt;


        private int step = 0;

        public void action() {
            switch (step) {
                case 0:

                    //update bidder list
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("bidder");
                    template.addServices(sd);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        if(result.length > 1){
                            countTick = countTick+1;
                        }
                        System.out.println("Found acutioneer agents:");
                        bidderAgent = new AID[result.length];
                        for (int i = 0; i < result.length; ++i) {
                            bidderAgent[i] = result[i].getName();
                            System.out.println(bidderAgent[i].getName());
                            System.out.println("tick time:" + countTick);
                        }
                    }
                    catch (FIPAException fe) {
                        fe.printStackTrace();
                    }

                    // Send the cfp to all sellers (Sending water volumn required to all bidding agent)
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    for (int i = 0; i < bidderAgent.length; ++i) {
                        if (bidderAgent[i].getName().equals(farmerInfo.farmerName)== false) {
                            cfp.addReceiver(bidderAgent[i]);
                        }
                    }
                    if(farmerInfo.currentPricePerMM >= farmerInfo.pricePerMM){
                        cfp.setContent(String.valueOf(Double.toString(farmerInfo.waterVolumn)+ "-"
                                +Double.toString(farmerInfo.currentPricePerMM) + "-" + Integer.toString(farmerInfo.numBidder)));
                    }else {
                        cfp.setContent(String.valueOf(Double.toString(farmerInfo.waterVolumn) + "-" + Double.toString(farmerInfo.pricePerMM))+
                                "-" + Double.toString(farmerInfo.pricePerMM));
                    }
                    cfp.setConversationId("bidding");
                    cfp.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
                    myAgent.send(cfp);
                    System.out.println("cfp message :" + "\n" + cfp);
                    // Prepare the template to get proposals
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("bidding"),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                    step = 1;
                    System.out.println(step);
                    break;

                case 1:

                    // Receive all proposals/refusals from bidder agents
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        repliesCnt++;
                        // Reply received
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {
                            proposeCnt++;
                            System.out.println("Receive message: " + reply);
                            //Count number of bidder that is propose message for water price bidding.
                            // This is an offer
                            String biddedFromAcutioneer = reply.getContent();
                            String[] arrOfStr = biddedFromAcutioneer.split("-");
                            waterVolFromBidder = Double.parseDouble(arrOfStr[0]);
                            biddedPriceFromBidder = Double.parseDouble(arrOfStr[1]);


                            if (bestBidder == null || biddedPriceFromBidder > bestPrice) {
                                // This is the best offer at present
                                bestPrice = biddedPriceFromBidder;
                                farmerInfo.currentPricePerMM = bestPrice;
                                bestBidder = reply.getSender();
                            }
                        }else if (reply.getPerformative() == ACLMessage.REFUSE){
                            refuseCnt++;
                        }
                        farmerInfo.numBidder = proposeCnt;
                        System.out.println("The number of current bidding is " + repliesCnt + "\n");
                        farmerInfo.numBidder = repliesCnt;
                        System.out.println("Surrender agent number is " + refuseCnt + "\n");
                        System.out.println("Best price is from " + bestBidder +"\n");
                        System.out.println("Price : " + bestPrice + "\n");

                        if (repliesCnt >= bidderAgent.length) {
                            // We received all replies
                            step = 2;
                            System.out.println(step);
                        }
                    }else {
                        block();
                    }
                    break;
                case 2:
                    //if(refuseCnt >=1 && proposeCnt==1|| farmerInfo.numBidder ==1 && countTick > 5){
                    if(refuseCnt >=1 && proposeCnt==1|| farmerInfo.numBidder ==1 && countTick > 5){
                        step = 3;
                        System.out.println(step);
                    }else {
                        step = 0;
                        System.out.println(step);
                        refuseCnt = 0;
                        proposeCnt = 0;
                        repliesCnt = 0;
                    }
                    break;
                case 3:
                    // Send the purchase order to the seller that provided the best offer
                    ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    order.addReceiver(bestBidder);
                    order.setContent(String.valueOf(farmerInfo.currentPricePerMM));
                    order.setConversationId("bidding");
                    order.setReplyWith("order"+System.currentTimeMillis());
                    myAgent.send(order);
                    // Prepare the template to get the purchase order reply
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("bidding"),
                            MessageTemplate.MatchInReplyTo(order.getReplyWith()));

                    step = 4;
                    System.out.println(step);
                    break;
                case 4:
                    // Receive the purchase order reply
                    reply = myAgent.receive(mt);
                    if (reply != null) {
                        // Purchase order reply received
                        if (reply.getPerformative() == ACLMessage.INFORM) {
                            // Purchase successful. We can terminate
                            System.out.println(farmerInfo.farmerName +" successfully purchased from agent "+reply.getSender().getName());
                            System.out.println("Price = "+farmerInfo.currentPricePerMM);
                            myGui.displayUI(farmerInfo.farmerName +" successfully purchased from agent "+reply.getSender().getName().toString());
                            myGui.displayUI("Price = " + farmerInfo.currentPricePerMM);
                            myAgent.doDelete();
                            myGui.dispose();

                        }
                        else {
                            System.out.println("Attempt failed: requested water volumn already sold.");
                            myGui.displayUI("Attempt failed: requested water volumn already sold.");
                        }

                        step = 5;
                        System.out.println(step);
                        //doSuspend();

                    }
                    else {
                        block();
                    }
                    break;
            }
        }

        public boolean done() {
            if (step == 2 && bestBidder == null) {
                //System.out.println("Attempt failed: "+volumeToBuy+" not available for sale");
                myGui.displayUI("Attempt failed: do not have bidder now".toString());
            }
            return ((step == 2 && bestBidder == null) || step == 5);
        }
    }

    private class PurchaseOrdersServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                // ACCEPT_PROPOSAL Message received. Process it
                ACLMessage reply = msg.createReply();
                myGui.displayUI(msg.toString());
                System.out.println(farmerInfo.sellingStatus);
                reply.setPerformative(ACLMessage.INFORM);
                if (farmerInfo.sellingStatus=="avalable") {
                    farmerInfo.sellingStatus = "sold";
                    //System.out.println(getAID().getName()+" sold water to agent "+msg.getSender().getName());
                    myGui.displayUI(getAID().getLocalName()+" sold water to agent "+msg.getSender().getLocalName());
                    //myGui.displayUI(farmerInfo.sellingStatus.toString());
                    //System.out.println(farmerInfo.sellingStatus);
                    doSuspend();
                } else {
                    // The requested book has been sold to another buyer in the meanwhile .
                    reply.setPerformative(ACLMessage.FAILURE);
                    reply.setContent("not-available for sale");
                    myGui.displayUI("not avalable to sell");
                }

            }else {
                block();
            }
        }
    }
    protected void takeDown() {
        try {
            DFService.deregister(this);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
        // Printout a dismissal message
        System.out.println("Seller-agent "+getAID().getName()+" terminating.");
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
