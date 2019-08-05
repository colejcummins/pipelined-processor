import java.io.*;
import java.util.HashMap;
import java.util.Scanner;
import java.util.ArrayList;


public class lab4 {
    private final String HELP_MESSAGE; 
    private HashMap<String, Integer> regMap;
    private HashMap<Integer, String> reverMap;
    private HashMap<String, int[]> instrs;
    private ArrayList<Instruction> instructions;
    private ArrayList<Instruction> pipeline;
    private int[] regFile;
    private int[] dataMemory;
    private int programCounter;
    private int cycles;
    private int numInst;
    private int inPipeline;
    private boolean stalling;
    private boolean jumping;
    private boolean branching;

    public lab4(ArrayList<String> fileList) {
        this.regMap = initRegisters();
        this.instrs = initInstr();
        this.instructions = genInstrs(fileList, regMap, this.instrs);
        this.pipeline = new ArrayList<Instruction>();
        this.dataMemory = new int[8192];
        this.regFile = new int[32];
        this.programCounter = 0;
        this.cycles = 0;
        this.numInst = 0;
        this.inPipeline = 0;
        this.stalling = false;
        this.jumping = false;
        this.branching = false;
        this.reverMap = new HashMap<Integer, String>();
        this.HELP_MESSAGE = "\nh = show help\nd = dump register state\ns = single step" +
        " through the program (i.e. execute 1 instruction and st" +
        "op)\ns num = step through num instructions of the progr" +
        "am\nr = run until the program ends\nm num1 num2 = displ" +
        "ay data memory from location num1 to num2\nc = clear al" +
        "l registers, memory, and the program counter to 0\n" +
        "q = exit the program\n";
        
        for (HashMap.Entry<String, Integer> entry : this.regMap.entrySet()) {
            this.reverMap.put(entry.getValue(), entry.getKey());
        }

        for (int i = 0; i < 4; i++) {
            Instruction ins = new Instruction("empty", new ArrayList<Integer>());
            this.pipeline.add(i, ins);
        }
        
    }

    public String toString() {
        String out = "\npc = " + this.programCounter + "\n";
        int line = 0;
        for (int i = 0; i < 32; i++) {
            String regName = this.reverMap.get(i);
            if (regName == null) 
                continue;
            String regCont = Integer.toString(this.regFile[i]);
            out += String.format("%-15s", "$" + regName + " = " + regCont);
            if (line % 4 == 3) 
                out += "\n";
            line++;
        }
        return out + "\n";
    }

    
    public void printMemory(String str, String stp) {
        String out = "";
        int start = Integer.parseInt(str);
        int stop = Integer.parseInt(stp);

        for (int i = start; i <= stop; i++) 
            out += "[" + i + "] = " + this.dataMemory[i] + "\n";
        System.out.println(out);
    }


    public void printHelp() {
        System.out.println(this.HELP_MESSAGE);
    }

    
    public void clear() {
        System.out.println("\tSimulator reset");
        this.dataMemory = new int[8192];
        this.regFile = new int[32];
        this.programCounter = 0;
        this.cycles = 0;
        this.numInst = 0;
        this.pipeline = new ArrayList<Instruction>();
        for (int i = 0; i < 4; i++) {
            Instruction ins = new Instruction("empty", new ArrayList<Integer>());
            this.pipeline.add(i, ins);
        }
    }


    public void readScript(ArrayList<String> scriptList) {
        for(String s : scriptList) {
            System.out.println("mips> " + s);
            if (s.equals("q"))
                break;
            this.processCommand(s);
        }
    }


    public void step(int num) {
        String out = "";
        for (int i = 0; i < num; i++) {
            advancePipeline();
        }
        out += "pc      if/id   id/exe  exe/mem mem/wb\n";
        out += String.format("%-8d", this.programCounter);
        for (int j = 0; j < 4; j++) {
            out += String.format("%-8s", this.pipeline.get(j).name);
        }
        System.out.println("\n" + out);
    }


    public void run() {
        String out;
        while (this.programCounter < this.instructions.size()) {
            advancePipeline();
        }
        while (this.inPipeline > 0) {
            advancePipeline();
        }
        out = "\nProgram complete\nCPI = ";
        out += String.format("%-10.3f", ((float)this.cycles/(float)this.numInst));
        out += "Cycles = " + String.format("%-8d", this.cycles);
        out += "Instructions = " + this.numInst;
        System.out.println(out);
    }


    public void executeInstr(Instruction ins) {
        ArrayList<Integer> fl = ins.fields;
        switch (ins.name) {
            case "and": this.regFile[fl.get(0)] = this.regFile[fl.get(1)] & this.regFile[fl.get(2)];
                        break;
            case "or":  this.regFile[fl.get(0)] = this.regFile[fl.get(1)] | this.regFile[fl.get(2)];
                        break;
            case "add": this.regFile[fl.get(0)] = this.regFile[fl.get(1)] + this.regFile[fl.get(2)];
                        break;
            case "addi":this.regFile[fl.get(0)] = this.regFile[fl.get(1)] + fl.get(2);
                        break;
            case "sll": this.regFile[fl.get(0)] = this.regFile[fl.get(1)] << fl.get(2);
                        break;
            case "sub": this.regFile[fl.get(0)] = this.regFile[fl.get(1)] - this.regFile[fl.get(2)];
                        break;
            case "slt": if (this.regFile[fl.get(1)] < this.regFile[fl.get(2)]) 
                            this.regFile[fl.get(0)] = 1;
                        else 
                            this.regFile[fl.get(0)] = 0;
                        break;
            case "beq": if (this.regFile[fl.get(0)] == this.regFile[fl.get(1)])
                            this.programCounter += fl.get(2);
                        break;
            case "bne": if (this.regFile[fl.get(0)] != this.regFile[fl.get(1)])
                            this.programCounter += fl.get(2);
                        break;
            case "lw":  this.regFile[fl.get(0)] = this.dataMemory[this.regFile[fl.get(2)] + fl.get(1)];
                        break;
            case "sw":  this.dataMemory[this.regFile[fl.get(2)] + fl.get(1)] = this.regFile[fl.get(0)];
                        break;
            case "j":   this.programCounter = fl.get(0);
                        break;
            case "jr":  this.programCounter = this.regFile[fl.get(0)];
                        break;
            case "jal": this.regFile[31] = this.programCounter + 1;
                        this.programCounter = fl.get(0);
                        break;
        }
        if (!ins.name.equals("j") && !ins.name.equals("jr") && !ins.name.equals("jal")) {
            this.programCounter++;
        }
    }


    public void advancePipeline() {
        writeBack();
        memory();
        execute();
        decode();
        fetch();
        this.cycles++;
    }

    /*
        Pipeline in:                        Pipeline out:
            fields[0] == reg_write_en           NONE
            fields[1] == saved
            fields[2] == reg_write_data/alu_out
            fields[3] == reg_write_addr
    */
    public void writeBack() {
        Instruction instr = this.pipeline.get(3);
        if (instrs.containsKey(instr.name)) {
            if (instr.fields.get(0) == 1);
                this.regFile[instr.fields.get(3)] = instr.fields.get(2);
            if (this.instrs.containsKey(instr.name)) {
                this.inPipeline--;
                this.numInst++;
            }
        }
        if (this.pipeline.size() > 4)
            this.pipeline.remove(4);
    }

    /*
        Pipeline in:                        Pipeline out:
            fields[0] == reg_write_en           fields[0] == reg_write_en
            fields[1] == mem_write_data         fields[1] == saved
            fields[2] == alu_out                fields[2] == reg_write_data/alu_out
            fields[3] == reg_write_addr         fields[3] == reg_write_addr
    */
    public void memory() {
        Instruction instr = this.pipeline.get(2);
        if (instr.name.equals("sw"))
            this.dataMemory[instr.fields.get(2)] = instr.fields.get(1);
        else if (instr.name.equals("lw")) {
            instr.fields.remove(2);
            instr.fields.add(2, this.dataMemory[instr.fields.get(1)]);
        } else if (instr.name.equals("beq")) {
            if (this.regFile[instr.fields.get(5)] == this.regFile[instr.fields.get(6)])
                this.branching = true;
        } else if (instr.name.equals("bne")) {
            if (this.regFile[instr.fields.get(5)] != this.regFile[instr.fields.get(6)])
                this.branching = true;
        }
    }


    /*
        Pipeline in:                        Pipeline out:
            fields[0] == reg_write_en           fields[0] == reg_write_en
            fields[1] == mem_write_data         fields[1] == mem_write_data
            fields[2] == reg_data_1             fields[2] == alu_out
            fields[3] == reg_write_addr         fields[3] == re_write_addr
            fields[4] == reg_data_2/imm
            fields[5] == reg_addr_1
            fields[6] == reg_addr_2
    */
    public void execute() {
        Instruction instr = this.pipeline.get(1);
        Instruction memFwd = this.pipeline.get(2);
        Instruction wbFwd = this.pipeline.get(3);

        //Memory Forwarding block
        if (this.instrs.containsKey(instr.name)) {
            if (this.instrs.containsKey(wbFwd.name) && (wbFwd.fields.get(0) == 1)) {
                if (wbFwd.fields.get(3) == instr.fields.get(5)) {
                    instr.fields.remove(2);
                    instr.fields.add(2, wbFwd.fields.get(2));
                } if (wbFwd.fields.get(3) == instr.fields.get(6)) {
                    instr.fields.remove(4);
                    instr.fields.add(4, wbFwd.fields.get(2));
                }
            }
            if (this.instrs.containsKey(memFwd.name) && (memFwd.fields.get(0) == 1)) {
                if (memFwd.fields.get(3) == instr.fields.get(5)) {
                    instr.fields.remove(2);
                    instr.fields.add(2, memFwd.fields.get(2));
                } if (memFwd.fields.get(3) == instr.fields.get(6)) {
                    instr.fields.remove(4);
                    instr.fields.add(4, memFwd.fields.get(2));
                }
            } 
        }

        int save;
        switch (instr.name) {
            case "and":
                save = instr.fields.remove(2);
                instr.fields.add(2, save & instr.fields.get(3));
                break;
            case "or":
                save = instr.fields.remove(2);
                instr.fields.add(2, save | instr.fields.get(3));
                break;
            case "add": case "addi": case "sw": case "lw":
                save = instr.fields.remove(2);
                instr.fields.add(2, save + instr.fields.get(3));
                break;
            case "sub":
                save = instr.fields.remove(2);
                instr.fields.add(2, save - instr.fields.get(3));
                break;
            case "sll":
                save = instr.fields.remove(2);
                instr.fields.add(2, save << instr.fields.get(3));
                break;
            case "slt":
                save = instr.fields.remove(2);
                int out = (save < instr.fields.get(3)) ? 1 : 0;
                instr.fields.add(2, out);
                break;
        }
    }


    /*
        Pipeline in:                        Pipeline out:
            fields[0] == Rd/imm                 fields[0] == reg_write_en
            fields[1] == Rt/imm                 fields[1] == mem_write_data
            fields[2] == Rs/imm                 fields[2] == reg_data_1
                                                fields[3] == reg_write_addr
                                                fields[4] == reg_data_2/imm
    */
    public void decode() {
        Instruction instr = this.pipeline.get(0);
        Instruction afterLoad = this.pipeline.get(1);
        ArrayList<Integer> fieldsIn = instr.fields;
        ArrayList<Integer> fieldsOut = new ArrayList<Integer>();
        // Stall for use after load 
        if (afterLoad.name.equals("lw")) {
            if (!(afterLoad.fields.get(3) == 0 && fieldsIn.get(2) == 0) &&
                ((afterLoad.fields.get(3) == fieldsIn.get(1)) ||
                (afterLoad.fields.get(3) == fieldsIn.get(2)))) {
                    Instruction stall = new Instruction("stall", new ArrayList<Integer>());
                    this.pipeline.add(1, stall);
                    this.stalling = true;
                    return;
            }
        }

        switch (instr.name) {
            case "and": case "or": case "add": case "sub": case "addi": 
            case "sll": case "slt":
                fieldsOut.add(1);    
                fieldsOut.add(0);
                fieldsOut.add(this.regFile[fieldsIn.get(1)]);
                fieldsOut.add(fieldsIn.get(0));
                if (!(instr.name.equals("addi") || instr.name.equals("sll")))
                    fieldsOut.add(this.regFile[fieldsIn.get(2)]);
                else 
                    fieldsOut.add(fieldsIn.get(2));
                fieldsOut.add(fieldsIn.get(1));
                fieldsOut.add(fieldsIn.get(2));
                break;
            case "sw": case "lw":
                int load = instr.name.equals("lw") ? 1 : 0;
                fieldsOut.add(load);    
                fieldsOut.add(this.regFile[fieldsIn.get(0)]);
                fieldsOut.add(fieldsIn.get(1)); 
                fieldsOut.add(fieldsIn.get(0));
                fieldsOut.add(this.regFile[fieldsIn.get(2)]);
                fieldsOut.add(0);   
                fieldsOut.add(0);
                break;
            case "j": case "jr":
                fieldsOut.add(0);   fieldsOut.add(0);   fieldsOut.add(0);
                fieldsOut.add(0);   fieldsOut.add(0);   fieldsOut.add(0);
                if (instr.name.equals("j"))
                    fieldsOut.add(fieldsIn.get(0));
                else 
                    fieldsOut.add(this.regFile[fieldsIn.get(0)]);
                this.jumping = true;
                break;
            case "beq": case "bne":
                fieldsOut.add(0);   fieldsOut.add(0);   fieldsOut.add(0);
                fieldsOut.add(0);   fieldsOut.add(fieldsIn.get(2));
                fieldsOut.add(fieldsIn.get(0));
                fieldsOut.add(fieldsIn.get(1));
                break;
            case "jal":
                fieldsOut.add(1);   fieldsOut.add(0);   fieldsOut.add(this.programCounter);
                fieldsOut.add(31);  fieldsOut.add(0);   fieldsOut.add(0);   
                fieldsOut.add(fieldsIn.get(0));
                this.jumping = true;
                break;
        }

        instr.fields = fieldsOut;
    }


    public void fetch() {
        if (!this.stalling && !this.jumping && !this.branching) {
            Instruction instr;
            Instruction save;
            ArrayList<Integer> nList = new ArrayList<Integer>();
            if (this.programCounter < this.instructions.size()) {
                save = this.instructions.get(this.programCounter);
                for (int i : save.fields)
                    nList.add(i);
                instr = new Instruction(save.name, nList);
                this.inPipeline++;
            } else 
                instr = new Instruction("empty", new ArrayList<Integer>());
            this.pipeline.add(0, instr);
            this.programCounter++;
        } if (this.stalling)
            this.stalling = false;
        if (this.jumping) {
            Instruction getJump = this.pipeline.get(0);
            this.pipeline.add(0, new Instruction("squash", new ArrayList<Integer>()));
            this.programCounter = getJump.fields.get(6);
            this.jumping = false;
        } if (this.branching) {
            Instruction getBranch = this.pipeline.get(2);
            this.pipeline.remove(0);
            this.pipeline.remove(0);
            this.inPipeline -= 2;
            for (int i = 0; i < 3; i++) {
                this.pipeline.add(0, new Instruction("squash", new ArrayList<Integer>()));
            }
            this.programCounter += getBranch.fields.get(4) - 2;
            this.branching = false;
        }
    }


    public void processCommand(String command) {
        String[] splitStr = command.split(" ");

        switch (splitStr[0]) {
            case "c":   this.clear();
                        break;    
            case "d":   System.out.println(this); 
                        break;
            case "m":   printMemory(splitStr[1], splitStr[2]);
                        break;
            case "r":   this.run();
                        break;
            case "s":   if (splitStr.length == 1)
                            this.step(1);
                        else 
                            this.step(Integer.parseInt(splitStr[1]));
                        break;
            case "q":   break;

            default:    this.printHelp(); break;
        }
    }


    public static void main(String[] args) {
        BufferedReader reader;
        if (args.length == 0) {
            System.out.println("file not found error");
            return;
        }

        try {
            reader = new BufferedReader( new FileReader(args[0]));
        } catch(Exception e) {
            System.out.println("file not found error");
            return;
        }

        ArrayList<String> fileList = readFile(reader);
        lab4 emulator = new lab4(fileList);
        if (args.length == 2) {
            BufferedReader script;
            try {
                script = new BufferedReader( new FileReader(args[1]));
            } catch(Exception e) {
                System.out.println("unable to read script error");
                return;
            }
    
            ArrayList<String> scriptList = readFile(script);
            emulator.readScript(scriptList);
        } else {
            Scanner sc = new Scanner(System.in);
            String inp;
            do {
                System.out.print("mips> ");
                inp = sc.nextLine();
                emulator.processCommand(inp);
            } while(!inp.equals("q"));
            sc.close();
        }
    }



// -------------------- LAB 2 IMPORTED CODE --------------------
    /* generates an arraylist of Instructions from the input file for processing
    */
    public ArrayList<Instruction> genInstrs(ArrayList<String> fileList, HashMap<String, Integer> registers, 
                                            HashMap<String, int[]> instrs) {
        
        HashMap<String, Integer> labels = createLabels(fileList, instrs);
        ArrayList<Instruction> instructions = new ArrayList<Instruction>();

        int lineNum = 0;
        for (String line : fileList) {
            ArrayList<String> parsed = parseLine(line, false);
            if (parsed == null)
                continue;

            String name = parsed.get(0);
            if (name.equals("beq") || name.equals("bne")) {
                Integer label = labels.get(parsed.get(3));
                parsed.set(3, Integer.toString(label - (lineNum + 1)));
            }

            if (name.equals("j") || name.equals("jal")) {
                Integer label = labels.get(parsed.get(1));
                parsed.set(1, Integer.toString(label));
            }

            ArrayList<Integer> fields = new ArrayList<Integer>();
            for (int i = 1; i < parsed.size(); i++) {
                String s = parsed.get(i);
                if (registers.containsKey(s)) {
                    fields.add(registers.get(s));
                } else {
                    fields.add(Integer.parseInt(s));
                }
            }

            Instruction e = new Instruction(name, fields);
            instructions.add(e);
            lineNum++;
        }

        return instructions;
    }


    /* Reads the file line by line to generate a list of lines
    */
    public static ArrayList<String> readFile(BufferedReader reader) {
        ArrayList<String> fileList = new ArrayList<String>();
        String line = "";

        do {
            try {
                line = reader.readLine();
                fileList.add(line);
            } catch(Exception e) {
                System.out.println("file read error");
            }
        } while(line != null);

        fileList.remove(fileList.size() - 1);
        return fileList;
    }


    /* createLabels is the first pass of the two pass compiler, taking the
    embeded labels and hashing them with their line number 
    */
    public static HashMap<String, Integer> createLabels(ArrayList<String> fileList, HashMap<String, int[]> instr) {
        HashMap<String, Integer> labels = new HashMap<String, Integer>();
        int lineNum = 0;
        for (String s : fileList) {  
            ArrayList<String> split = parseLine(s, true);
            
            if (split != null) {
                String fStr = split.get(0);
                if (fStr.charAt(fStr.length() - 1) == ':') {
                    labels.put(fStr.substring(0, fStr.length() - 1), lineNum);
                }
                if (split.size() > 1 && instr.containsKey(split.get(1))) {
                    lineNum++;
                    continue;
                }
                if (instr.containsKey(fStr))
                    lineNum++;
                
            }
        }
        return labels;
    }


    /* parseline takes user input, parses each character at a time,
    and returns a valid instruction
    Blank lines and commented lines are returned as null
    if the boolean labels as true, labels will be counted as lines, otherwise
    labels will be ignored 
    */
    public static ArrayList<String> parseLine(String line, boolean labels) {
        ArrayList<String> lineList = new ArrayList<String>();
        StringBuffer strBuf = new StringBuffer();
        
        for (int i = 0; i < line.length(); i++) {

            char c = line.charAt(i);
            if ((c == '\n') || (c == '#')) {
                if (strBuf.length() > 0) 
                    lineList.add(strBuf.toString());
                break;
            } else if ((c == ' ') || (c == ',') || (c == '\t') || 
                       (c == '(') || (c == ')') || (c == '$')) {
                if (strBuf.length() > 0) {
                    lineList.add(strBuf.toString());
                    strBuf = new StringBuffer();
                }
            } else 
                strBuf.append(c);

            if (c == ':') {
                if (labels) lineList.add(strBuf.toString());
                strBuf = new StringBuffer();
            }

            if ((i == line.length() - 1) && (strBuf.length() > 0))
                lineList.add(strBuf.toString());
        }

        if (lineList.size() == 0)
            return null;
        return lineList;
    }

    
    private class Instruction {
        private String name;
        private ArrayList<Integer> fields;

        private Instruction(String name, ArrayList<Integer> fields) {
            this.name = name;
            this.fields = fields;
        }

        public String toString() {
            String out = this.name + ":\n";
            for (Integer i : this.fields) {
                out += Integer.toString(i) + " ";
            }
            out += "\n";
            return out;
        }
    }

    /* initRegisters creates a hashmap of register names to numbers 
    */
    public static HashMap<String, Integer> initRegisters() {
        HashMap<String, Integer> regs = new HashMap<String, Integer>(50);
        regs.put("0", 0);      regs.put("v0", 2);     regs.put("v1", 3);
        regs.put("a0", 4);     regs.put("a1", 5);     regs.put("a2", 6);
        regs.put("a3", 7);     regs.put("t0", 8);     regs.put("t1", 9);
        regs.put("t2", 10);    regs.put("t3", 11);    regs.put("t4", 12);
        regs.put("t5", 13);    regs.put("t6", 14);    regs.put("t7", 15);
        regs.put("s0", 16);    regs.put("s1", 17);    regs.put("s2", 18);
        regs.put("s3", 19);    regs.put("s4", 20);    regs.put("s5", 21);
        regs.put("s6", 22);    regs.put("s7", 23);    regs.put("t8", 24);
        regs.put("t9", 25);    regs.put("sp", 29);    regs.put("ra", 31);
        return regs;
    }


    public static HashMap<String, int[]> initInstr() {
        HashMap<String, int[]> inst = new HashMap<String, int[]>(25);

        inst.put("and", new int[] {0, 6, 2, 5, 3, 5, 1, 5, 36, 11});
        inst.put("or", new int[] {0, 6, 2, 5, 3, 5, 1, 5, 37, 11});
        inst.put("add", new int[] {0, 6, 2, 5, 3, 5, 1, 5, 32, 11});
        inst.put("sub", new int[] {0, 6, 2, 5, 3, 5, 1, 5, 34, 11});
        inst.put("slt", new int[] {0, 6, 2, 5, 3, 5, 1, 5, 42, 11});

        inst.put("addi", new int[] {0, 6, 2, 5, 1, 5, 3, 16});
        inst.put("sll", new int[] {0, 11, 2, 5, 1, 5, 3, 5, 0, 6});

        inst.put("beq", new int[] {4, 6, 1, 5, 2, 5, 3, 16});
        inst.put("bne", new int[] {5, 6, 1, 5, 2, 5, 3, 16});
        inst.put("lw", new int[] {35, 6, 3, 5, 1, 5, 2, 16});
        inst.put("sw", new int[] {43, 6, 3, 5, 1, 5, 2, 16});

        inst.put("j", new int[] {2, 6, 1, 26});
        inst.put("jr", new int[] {0, 6, 1, 5, 8, 21});
        inst.put("jal", new int[] {3, 6, 1, 26});

        return inst;
    }
}
