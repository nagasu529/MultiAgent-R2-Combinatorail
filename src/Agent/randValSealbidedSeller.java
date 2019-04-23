package Agent;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.text.DecimalFormat;
import java.util.*;

public class randValSealbidedSeller extends Agent {
    randValSealbidedSellerGUI myGui;

    //General papameter information
    DecimalFormat df = new DecimalFormat("#.##");
    randValue randValue = new randValue();
    agentInfo sellerInfo = new agentInfo("", "seller", randValue.getRandDoubleRange(10,12), randValue.getRandDoubleRange(5000,13000), 0, 0.0, "", 0);
    //Instant papameter for AID[]
    AID[] bidderAgents;

    protected void setup(){
        myGui = new randValSealbidedSellerGUI(this);
        myGui.show();
        myGui.displayUI(getAID().getLocalName() + " is active");

        //Agent registered to environment.

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        sellerInfo.farmerName = getAID().getLocalName();
        ServiceDescription sd = new ServiceDescription();
        sd.setType(sellerInfo.agentType);
        try {
            DFService.register(this, dfd);
        }catch (FIPAException fe){
            fe.printStackTrace();
        }



        addBehaviour(new TickerBehaviour(this, 2000) {
            protected void onTick() {
                myGui.displayUI("Selling price ($): " + sellerInfo.sellingPrice + "   " + "Volumn to sell: " + sellerInfo.sellingVolumn + "\n");
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("bidder");
                template.addServices(sd);
                try {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    System.out.println("Found the following seller agents:");
                    bidderAgents = new AID[result.length];
                    for (int i = 0; i < result.length; ++i) {
                        bidderAgents[i] = result[i].getName();
                        System.out.println(bidderAgents[i].getName());
                    }
                }
                catch (FIPAException fe) {
                    fe.printStackTrace();
                }
                //perform the request.
                addBehaviour(new OfferRequestsServer());
            }
        });

        addBehaviour(new TickerBehaviour(this, 20000) {
            protected void onTick() {
                addBehaviour(new RequestPerformer());
            }
        });
    }

    protected void takeDown(){
        try{
            DFService.deregister(this);
        }catch (FIPAException fe){
            fe.printStackTrace();
        }
        myGui.dispose();
        System.out.println(getAID().getLocalName() + " is terminated");
    }
    private class OfferRequestsServer extends CyclicBehaviour{
        public void action(){
            String tempMsg;
            Double tempVolumn;
            Double tempPrice;

            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);
            if(msg != null){
                //received CFP messages and process.
                tempMsg = msg.getContent();
                String[] arrOfStr = tempMsg.split("-");
                tempVolumn = Double.parseDouble(arrOfStr[0]);
                tempPrice = Double.parseDouble(arrOfStr[1]);
                if(sellerInfo.sellingVolumn <= tempVolumn && tempPrice > sellerInfo.acceptedPrice){
                    sellerInfo.acceptedVolumn = tempVolumn;
                    sellerInfo.acceptedPrice = tempPrice;
                    sellerInfo.acceptedName = msg.getSender().getLocalName();
                }
            }else {
                block();
            }
        }
    }

    private class RequestPerformer extends Behaviour{
        private MessageTemplate mt; // The template to receive replies
        private int step = 0;
        private int repliesCnt =0;
        String tempMsg;
        Double tempVolumn;
        Double tempPrice;

        public void action(){
            switch (step) {
                case 0:
                //Receied all bidder mesasge.
                MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
                ACLMessage msg = myAgent.receive(mt);
                if(msg != null){
                    //received CFP messages and process.
                    tempMsg = msg.getContent();
                    String[] arrOfStr = tempMsg.split("-");
                    tempVolumn = Double.parseDouble(arrOfStr[0]);
                    tempPrice = Double.parseDouble(arrOfStr[1]);
                    if(sellerInfo.sellingVolumn <= tempVolumn && tempPrice > sellerInfo.acceptedPrice){
                        sellerInfo.acceptedVolumn = tempVolumn;
                        sellerInfo.acceptedPrice = tempPrice;
                        sellerInfo.acceptedName = msg.getSender().getLocalName();
                    }
                }else {
                    block();
                }
            }
            if(sellerInfo.acceptedName != null){


                for(int i=0; i < bidderAgents.length; i++){
                    if(bidderAgents[i].getLocalName().equals(sellerInfo.acceptedName)){
                        ACLMessage accptedOffer = new ACLMessage(ACLMessage.PROPOSE);
                        accptedOffer.addReceiver(bidderAgents[i]);
                        accptedOffer.setContent("accepted");
                    }else {
                        ACLMessage rejectedOffer = new ACLMessage(ACLMessage.REFUSE);
                        rejectedOffer.addReceiver(bidderAgents[i]);
                        rejectedOffer.setContent("sold");
                    }
                }
            }
        }

    }

    public class agentInfo{
        String farmerName;
        String agentType;
        Double sellingPrice;
        Double sellingVolumn;
        Double acceptedPrice;
        Double acceptedVolumn;
        String acceptedName;
        int numSeller;

        agentInfo(String farmerName, String agentType, double sellingPrice, double sellingVolumn, double acceptedPrice, double acceptedVolumn, String acceptedName, int numSeller){
            this.farmerName = farmerName;
            this.agentType = agentType;
            this.sellingPrice = sellingPrice;
            this.sellingVolumn = sellingVolumn;
            this.acceptedPrice = acceptedPrice;
            this.acceptedVolumn = acceptedVolumn;
            this.acceptedName = acceptedName;
            this.numSeller = numSeller;

        }
    }
}
