/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.ethereum.vm;

import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.AccountState;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.Repository;
import org.ethereum.crypto.HashUtil;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * @author Roman Mandeleil
 * @since 16.06.2014

 * Differences betwheen this class and the ones developed by Seba + Juli for Create2, ExtCodeHash.
    - this one uses null for transaction when instantiating a new program = in getProgram()

 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class VMComplexRentTest {

    private static Logger logger = LoggerFactory.getLogger("TCK-Test");
    private final TestSystemProperties config = new TestSystemProperties();
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private final VmConfig vmConfig = config.getVmConfig();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config, null);

    @Ignore //TODO #POC9
    @Test // contract call recursive
    public void test1() {

        /**
         *       #The code will run
         *       ------------------

                 a = contract.storage[999]
                 if a > 0:
                     contract.storage[999] = a - 1

                     # call to contract: 77045e71a7a2c50903d88e564cd72fab11e82051
                     send((tx.gas / 10 * 8), 0x77045e71a7a2c50903d88e564cd72fab11e82051, 0)
                 else:
                     stop
         */

        int expectedGas = 436;

        DataWord key1 = DataWord.valueOf(999);
        DataWord value1 = DataWord.valueOf(3);

        // Set contract into Database
        String callerAddr = "cd2a3d9f938e13cd947ec05abc7fe734df8dd826";
        String contractAddr = "77045e71a7a2c50903d88e564cd72fab11e82051";
        String code =
                "6103e75460005260006000511115630000004c576001600051036103e755600060006000600060007377045e71a7a2c50903d88e564cd72fab11e820516008600a5a0402f1630000004c00565b00";

        RskAddress contractAddrB = new RskAddress(contractAddr);
        RskAddress callerAddrB = new RskAddress(callerAddr);
        byte[] codeB = Hex.decode(code);

        byte[] codeKey = HashUtil.keccak256(codeB);
        AccountState accountState = new AccountState();
        //accountState.setCodeHash(codeKey);

        ProgramInvokeMockImpl pi = new ProgramInvokeMockImpl();
        pi.setOwnerAddress(contractAddrB);
        Repository repository = pi.getRepository();

        repository.createAccount(callerAddrB);
        final BigInteger value = new BigInteger("100000000000000000000");
        repository.addBalance(callerAddrB, new Coin(value));

        repository.createAccount(contractAddrB);
        repository.saveCode(contractAddrB, codeB);
        repository.addStorageRow(contractAddrB, key1, value1);

        // Play the program
        VM vm = getSubject();
        Program program = getProgram(codeB, pi);

        try {
            while (!program.isStopped())
                vm.step(program);
        } catch (RuntimeException e) {
            program.setRuntimeFailure(e);
        }

        System.out.println();
        System.out.println("============ Results ============");

        Coin balance = repository.getBalance(callerAddrB);

        System.out.println("*** Used gas: " + program.getResult().getGasUsed());
        System.out.println("*** Contract Balance: " + balance);

        // todo: assert caller balance after contract exec

        assertEquals(expectedGas, program.getResult().getGasUsed());
    }
    
    
    @Ignore //TODO #POC9
    @Test // contractB call contractA with data to storage
    public void test2() {

        /**
         *       #The code will run
         *       ------------------

                 contract A: 77045e71a7a2c50903d88e564cd72fab11e82051
                 ---------------
                     a = msg.data[0]
                     b = msg.data[1]

                     contract.storage[a]
                     contract.storage[b]


                 contract B: 83c5541a6c8d2dbad642f385d8d06ca9b6c731ee
                 -----------
                     a = msg((tx.gas / 10 * 8), 0x77045e71a7a2c50903d88e564cd72fab11e82051, 0, [11, 22, 33], 3, 6)

         */

        long expectedVal_1 = 11;
        long expectedVal_2 = 22;

        // Set contract into Database
        String caller_addr_str = "cd2a3d9f938e13cd947ec05abc7fe734df8dd826";

        String contractA_addr_str = "77045e71a7a2c50903d88e564cd72fab11e82051";
        String contractB_addr_str = "83c5541a6c8d2dbad642f385d8d06ca9b6c731ee";

        String code_a = "60006020023560005260016020023560205260005160005560205160015500";
        String code_b = "6000601f5360e05960e05952600060c05901536060596020015980602001600b9052806040016016905280606001602190526080905260007377045e71a7a2c50903d88e564cd72fab11e820516103e8f1602060000260a00160200151600052";

        RskAddress caller_addr = new RskAddress(caller_addr_str);

        RskAddress contractA_addr = new RskAddress(contractA_addr_str);
        byte[] codeA = Hex.decode(code_a);

        RskAddress contractB_addr = new RskAddress(contractB_addr_str);
        byte[] codeB = Hex.decode(code_b);

        ProgramInvokeMockImpl pi = new ProgramInvokeMockImpl();
        pi.setOwnerAddress(contractB_addr);
        Repository repository = pi.getRepository();

        repository.createAccount(contractA_addr);
        repository.saveCode(contractA_addr, codeA);

        repository.createAccount(contractB_addr);
        repository.saveCode(contractB_addr, codeB);

        repository.createAccount(caller_addr);
        final BigInteger value = new BigInteger("100000000000000000000");
        repository.addBalance(caller_addr, new Coin(value));


        // ****************** //
        //  Play the program  //
        // ****************** //
        VM vm = getSubject();
        Program program = getProgram(codeB, pi);

        try {
            while (!program.isStopped())
                vm.step(program);
        } catch (RuntimeException e) {
            program.setRuntimeFailure(e);
        }


        System.out.println();
        System.out.println("============ Results ============");


        System.out.println("*** Used gas: " + program.getResult().getGasUsed());


        DataWord value_1 = repository.getStorageValue(contractA_addr, DataWord.valueOf(00));
        DataWord value_2 = repository.getStorageValue(contractA_addr, DataWord.valueOf(01));


        assertEquals(expectedVal_1, value_1.longValue());
        assertEquals(expectedVal_2, value_2.longValue());

        // TODO: check that the value pushed after exec is 1
    }

    @Ignore
    @Test // contractB call contractA with return expectation
    public void test3() {

        /**
         *       #The code will run
         *       ------------------

         contract A: 77045e71a7a2c50903d88e564cd72fab11e82051
         ---------------

           a = 11
           b = 22
           c = 33
           d = 44
           e = 55
           f = 66

           [asm  192 0 RETURN asm]



         contract B: 83c5541a6c8d2dbad642f385d8d06ca9b6c731ee
         -----------
             a = msg((tx.gas / 10 * 8), 0x77045e71a7a2c50903d88e564cd72fab11e82051, 0, [11, 22, 33], 3, 6)

         */

        long expectedVal_1 = 11;
        long expectedVal_2 = 22;
        long expectedVal_3 = 33;
        long expectedVal_4 = 44;
        long expectedVal_5 = 55;
        long expectedVal_6 = 66;

        // Set contract into Database
        RskAddress caller_addr = new RskAddress("cd2a3d9f938e13cd947ec05abc7fe734df8dd826");

        RskAddress contractA_addr = new RskAddress("77045e71a7a2c50903d88e564cd72fab11e82051");
        RskAddress contractB_addr = new RskAddress("83c5541a6c8d2dbad642f385d8d06ca9b6c731ee");

        byte[] codeA = Hex.decode("600b60005260166020526021604052602c6060526037608052604260a05260c06000f2");
        byte[] codeB = Hex.decode("6000601f5360e05960e05952600060c05901536060596020015980602001600b9052806040016016905280606001602190526080905260007377045e71a7a2c50903d88e564cd72fab11e820516103e8f1602060000260a00160200151600052");

        ProgramInvokeMockImpl pi = new ProgramInvokeMockImpl();
        pi.setOwnerAddress(contractB_addr);
        Repository repository = pi.getRepository();
        repository.createAccount(contractA_addr);
        repository.saveCode(contractA_addr, codeA);

        repository.createAccount(contractB_addr);
        repository.saveCode(contractB_addr, codeB);

        repository.createAccount(caller_addr);
        final BigInteger value = new BigInteger("100000000000000000000");
        repository.addBalance(caller_addr, new Coin(value));

        // ****************** //
        //  Play the program  //
        // ****************** //
        VM vm = getSubject();
        Program program = getProgram(codeB, pi);

        try {
            while (!program.isStopped())
                vm.step(program);
        } catch (RuntimeException e) {
            program.setRuntimeFailure(e);
        }

        System.out.println();
        System.out.println("============ Results ============");
        System.out.println("*** Used gas: " + program.getResult().getGasUsed());

        DataWord value1 = program.memoryLoad(DataWord.valueOf(32));
        DataWord value2 = program.memoryLoad(DataWord.valueOf(64));
        DataWord value3 = program.memoryLoad(DataWord.valueOf(96));
        DataWord value4 = program.memoryLoad(DataWord.valueOf(128));
        DataWord value5 = program.memoryLoad(DataWord.valueOf(160));
        DataWord value6 = program.memoryLoad(DataWord.valueOf(192));

        assertEquals(expectedVal_1, value1.longValue());
        assertEquals(expectedVal_2, value2.longValue());
        assertEquals(expectedVal_3, value3.longValue());
        assertEquals(expectedVal_4, value4.longValue());
        assertEquals(expectedVal_5, value5.longValue());
        assertEquals(expectedVal_6, value6.longValue());

        // TODO: check that the value pushed after exec is 1
    }

    @Test // CREATE magic
    public void test4() {

        /**
         *       #The code will run
         *       ------------------

         contract A: 77045e71a7a2c50903d88e564cd72fab11e82051
         -----------

             a = 0x7f60c860005461012c6020540000000000000000000000000000000000000000
             b = 0x0060005460206000f20000000000000000000000000000000000000000000000
             create(100, 0 41)              //value = 100, memstart = 0, memsize = 41


         contract B: (the contract to be created the addr will be defined to: 8e45367623a2865132d9bf875d5cfa31b9a0cd94)
         -----------
             a = 200
             b = 300

         */

        // Set contract into Database
        RskAddress caller_addr = new RskAddress("cd2a3d9f938e13cd947ec05abc7fe734df8dd826");

        RskAddress contractA_addr = new RskAddress("77045e71a7a2c50903d88e564cd72fab11e82051");

        byte[] codeA = Hex.decode("7f7f60c860005461012c602054000000000000" + // c8 is 200, 012c is 300  read from A:a
                "00000000000000000000000000006000547e60" + // the 
                "005460206000f2000000000000000000000000" +
                "0000000000000000000000602054602960006064f0"); // 29 = 41, 00 = 0, 64 = 100 (value)  create(100, 0,41)

        ProgramInvokeMockImpl pi = new ProgramInvokeMockImpl();
        //pi.setOwnerAddress(caller_addr); // #mish
        pi.setOwnerAddress(contractA_addr);

        Repository repository = pi.getRepository();
        //System.out.println(repository.getAccountsKeys()); // 2 default addresses

        repository.createAccount(contractA_addr);
        repository.saveCode(contractA_addr, codeA);

        repository.createAccount(caller_addr);

        //#mish copied from createcontract in Program to see if the generated address matches what is in the example above
        RskAddress senderAddress = new RskAddress(pi.getOwnerAddress());
        final BigInteger value = new BigInteger("1000000");
        repository.addBalance(senderAddress, new Coin(value));
        byte[] nonce = repository.getNonce(senderAddress).toByteArray();
        byte[] newAddressBytes = HashUtil.calcNewAddr(pi.getOwnerAddress().getLast20Bytes(), nonce);
        RskAddress newAddress = new RskAddress(newAddressBytes);
        System.out.println("\nCreated and expected\n" + newAddress +  "\n8e45367623a2865132d9bf875d5cfa31b9a0cd94");
        System.out.println(repository.getBalance(newAddress));
        // ****************** //
        //  Play the program  //
        // ****************** //
        VM vm = getSubject();
        Program program = getProgram(codeA, pi);

        try {
            while (!program.isStopped())
                vm.step(program);
        } catch (RuntimeException e) {
            System.out.println("error");
            program.setRuntimeFailure(e);
        }

        logger.info("============ Results ============");
        System.out.println("*** Used gas: " + program.getResult().getGasUsed());
        System.out.println("*** Call create Size: " + program.getResult().getCallCreateList().size());
        System.out.println(repository.getBalance(newAddress)); // value is transferred
        System.out.println(repository.isContract(newAddress)); // returns true
        System.out.println(repository.getCode(newAddress)); //there is no code to save
        System.out.println(repository.getStorageKeysCount(newAddress)); //nothing stored.. strange
        // how many accounts? should be 3, but there are 4, one from invokemock default contract address
        System.out.println(repository.getAccountsKeys()); 


    }


    private VM getSubject() {
        return new VM(vmConfig, precompiledContracts);
    }

    private Program getProgram(byte[] code, ProgramInvoke pi) {
        return new Program(vmConfig, precompiledContracts, blockFactory, mock(ActivationConfig.ForBlock.class), code, pi, null, new HashSet<>());
    }
}