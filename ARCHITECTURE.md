# Architecture

## System Context Diagram

![](https://planttext.com/api/plantuml/svg/ZLJ1JW8n4BtlLuoS8D5uuMWCM_2WWOIuU2Q5ZjZORKdRtKYC_swxEuKYcdZQpUnxC--zcUp4UMvzKoke3ivZFJNQuuUVIRwpl2wkfDwgHY3SJSUtq_6QFjyNXPTU8LJbOWKtn0Nw2ebTCDDemUrXw97NvJKCLk49vM04dmN04gqpUltmuOSRlY84QvpKw1oKcglGTZ0wHmfi4tI6BIgpHzjXUWqTng5jvHQNrhHIe0tGggz0AWr2ZTuTS212wFFrGpHBejcbQ9B2_Y4b9EEQ6YtBjAHQr4BlBYbnlPIJ5iTh7xaXxuYlPBqFyPVBHrWOWKhg7QoCKU_IayvUGyShYGYKVZ_lLUuQYm8s1wg3Dx0D6-HImoPjeOxTGqHQm0urMkunvFN4spwqCyFTC7OsZFqnZCLXMSFbmRyP3LibKVlU-pEgRDcNAwN80pWYZmUK37UndE9CgcRLd1XPRw8STS8-EvCVj0iXYVtYyEX8X215FATy4ZkwoA_Xt4O6fInFel9x-LrKrlSohwZlTET_i6Zehru2PJrFEqwkd_4LBUeGSNygqVC4jGX_b6y0)

```plantuml
@startuml SystemContextDiagram
!include <C4/C4_Context>

title SAMT - System Context Diagram

Boundary(teamA, "Team A") {
  Person_Ext(baTeamA, "Business Analyst")
  Person_Ext(devTeamA, "Developer")
  System_Ext(serviceProvider, "Service Provider", "Provides a service")
}

Boundary(teamB, "Team B") {
  Person_Ext(devTeamB, "Developer")
  System_Ext(serviceConsumerB, "Service Consumer", "Consumes services")
}


Boundary(samt, "SAMT", "Simple API Modeling Toolkit") {
  System(samtA, "SAMT Team A", "Configured by Team A to contain business models and generate a Java server")
  System(samtB, "SAMT Team B", "Configured by Team B to generate a Python client")
}

Rel(devTeamA, serviceProvider, "Develop")
Rel(baTeamA, samtA, "Model services", "SAMT DSL")
Rel(devTeamA, samtA, "Configure provider", "SAMT DSL")
Rel(serviceProvider, samtA, "Use generated code")

Rel(devTeamB, serviceConsumerB, "Develop")
Rel(devTeamB, samtB, "Configure Consumer", "SAMT DSL")
Rel(serviceConsumerB, serviceProvider, "Use service")
Rel(serviceConsumerB, samtB, "Use generated code")
Rel_L(samtB, samtA, "References model")

@enduml
```

## Container Diagram (Complete)

![](https://planttext.com/api/plantuml/svg/ZPDDRzD048Rl-okc5eT4IjHBZr1Hd101HOGOpjLYJwobwzreFqmZn7zdrj-ENUZ5icTdtdbs_CwLKAdq6SBh-EOwldc8jN9SA3ItZ5rrPrBdsbu_QYU5kft4V_AIJwEbi9xBjNK-4tgTwWn9qWad4PAxpzzW3Lqls0YU6rumjaTZjNhOFhb5XYLAVKApWcZJlaTBnfApTLXKEI93ElsmsKOhU6LFHZXy4qqRXwftsgkCc_F2yi9HKM28bl2R0JI2eunO6soo8BmtgT13BoHNkUSv9cZAYlA-2K0T8QReKpMo5Tmd-_2vDxeaPWk1YXx5IQ8JMWtnwoaJyhXRI1QowXCthts3IfiRIyfXZWKtBr3CaPxSQVEUIl2NDe-aick3q4N7RUv-M6Tuwi8TYa8hw6JWw5KQRiEbSETd0FwmPmRUDRRjsFl8pF13oKVotG5j-9jVZaUH2YvhQuK3E6oJWSRe4lDWXCOkoV39eQ4BZUYsJN1AsF5LxcmPT-QK3ijwnlomZyUC8Jma3Bj5VCHsG5dxVUAx7JhgJ8QAXuQAWkUMLTbWbUgD-MuHd8RK87S81TNAi2CXzr9zu3jF_ceK--YiYr8kPKYj9UTIBlvVt1JyBxqPPPi-ItmX_TMZ4TNAgQF8FIdJV2OBkFodLDFsfLBiBQhC5_8l)

```plantuml
!include <C4/C4_Container>

!include <logos/kotlin>
!include <logos/java>
!include <logos/visual-studio-code>

title SAMT - Container Diagram

Person_Ext(developer, "Developer")

System_Ext(ide, "IDE", $sprite="visual-studio-code")

Boundary(samt, "SAMT") {
  System_Boundary(samtCore, "SAMT Core") {
    Container(languageServer, "Language Server", "Kotlin, LSP", "Provides code completion", $sprite="kotlin")
    Container(compiler, "Compiler", "Kotlin", "Parses grammar", $sprite="kotlin")
    Container(generatorFramework, "Generator Framework", "Kotlin", "Abstracts common generator functionality", $sprite="kotlin")
  }

  System_Boundary(samtGenerators, "SAMT Generators") {
    Container(samtJavaRest, "Official SAMT Java REST Generator", "Kotlin", $sprite="kotlin")
  }
}


System_Boundary(externalGenerators, "External Generators") {
  Container_Ext(samtJavaGrpc, "External SAMT Java gRPC Generator", "Java", $sprite="java")
}

Rel(ide, languageServer, "Get Code Completion", "LSP")
Rel(developer, ide, "Use to create model")

Rel(languageServer, compiler, "uses")
Rel(compiler, generatorFramework, "calls")


Rel(generatorFramework, samtJavaRest, "Calls")
Rel(generatorFramework, samtJavaGrpc, "Calls")

Rel(languageServer, samtJavaRest, "Queries configuration code completion")
Rel(languageServer, samtJavaGrpc, "Queries configuration code completion")

@enduml
```

## Container Diagram (Simplified)
![](https://planttext.com/api/plantuml/svg/ZLHDRzim3BtxLt163mcGe9SUrwATanNTMZIBktF1sOmXL9O2YV8Q3FtlesmdSJuElHWAzVZUqoEHMy_eEDGQCciObI4tKrWvh9CloXGwb0HViwlBxEfr1xX91dVQLjPVlbdMojoSr1lb0-gfvr0gEoriITBCYZL1VlVq0jEzB6nramKvRyth_9r79JMaxPhS1DBPzfoEaoJVUAQwnQYIvFPXDaydSE7NJZ5TfwV2iUkR3QP4jnbvh5cQeeTq3FyIW9xm095PHpqAuhc7mjxqIAEf0bQKasjQbuzz0RgAU4f_jk5Cu35Vn9yBPnlnx26waa-zriJAcg7zBa-nVAWMmKgtEbb_t0kqtEYyCCSSQtIVOAn8liZMVPSM-clTcx3SRukmgnxAtFtnxB3W-8QwjWPsDB0AfeYlGQruyma3CUSVsE2IV9p8ysgb2eMwMuzu0Sjv_W8xImT6pf5_91z9iYJTxSN9VEw9HLs2puR1ft4suxPji7BTPlts16oXS8HCK4klZhiKiSViW_a4cKFFkA-UJRj0hIEqepm7ESeespQSit24_HN8gNRNp4fLGPZbmIUR9rotP4hv6_WF)

```plantuml
@startuml ContainerDiagram
!include <C4/C4_Container>

!include <logos/kotlin>
!include <logos/visual-studio-code>

title SAMT - Container Diagram

Person_Ext(developer, "Developer")

System_Ext(ide, "IDE", $sprite="visual-studio-code")

Boundary(samt, "SAMT") {
  System_Boundary(samtCore, "SAMT Core") {
    Container(languageServer, "Language Server", "Kotlin, LSP", "Provides code completion", $sprite="kotlin")
    Container(compiler, "Compiler", "Kotlin", "Parses grammar", $sprite="kotlin")
    Container(generatorFramework, "Generator Framework", "Kotlin", "Abstracts common generator functionality", $sprite="kotlin")
    Container(samtJavaRest, "Official SAMT Java REST Generator", "Kotlin", $sprite="kotlin")
  }
}

Rel(ide, languageServer, "Get Code Completion", "LSP")
Rel(developer, ide, "Use to create model")

Rel(languageServer, compiler, "uses")
Rel(compiler, generatorFramework, "calls")


Rel(generatorFramework, samtJavaRest, "Calls")

Rel(languageServer, samtJavaRest, "Queries configuration code completion")

@enduml
```

## Workflow Diagram

![](https://planttext.com/api/plantuml/svg/dLD1RkCm3Bph5JocbpwWEMnxMg10iGMwm9wvCkrmAv429QNmxrUMdIPfUwfF0MSoC-JOzxcWbHg3SuLB87ZXc0AFJ0EyF3pzXZzghsFGD-Swqxd9jAOL-qb2XeLLd0EuIsYP86CijrvbtFSXIQv6C50Y6KWyKmPS1lecGN6WON_nPmm1RXS563bGxX2Fi5jGbWd8J2t_k85o887TJFccCPOd5qtj9uMciXDTnisGnovkLI1JH2dimH_8lvATnT-HxwcFxKATrcqUyOWmhOTHssyuu8GiPzRpN0ugrxmc295i8dSAzWv_T--1SmeuGwuneisW8tYDYKClhHrWyUo5ddhzbHO40pnOuH5zWUVwDt0-jEjVB-irpQS5silIz_Ow4B0KXISyjg39z1_viZyl7llSA12sm2dKYGKQdyKRHP82nrPD3xUvB75fbSzQpTE52suXBmmsupNqL-V_a7jhugov3QwZVuhNkAsiWWCMt3YRvofL--kQE7rezkBWlJbttoRjy_iF)
```plantuml
@startuml

title Simplified SAMT Workflow

start

group SAMT Generation

  :parse samt.conf;

  :ensure dependencies are downloaded;

  :parse all .samt files;

  if (parsing errors?) then (yes)
      stop
  else (no)
  endif

  :run semantic checkers;

  if (semantic errors?) then (yes)
      stop
  else (no)
  endif

  fork
    :run Java generator;
  fork again
    :run Python generator;
  end merge


  if (generator errors?) then (yes)
      stop
  else (no)
  endif

  :display summary of compilation;

end group

group Optional Artifact Publishing

  fork
    :copy generated Java code into Maven project;
    :run maven release plugin to increment version;
    :compile Java source code into artifact;
    :run Maven publish;
  fork again
    :copy generated Python code into Python project;
    :increment version in metadata;
    :publish code using pip;
  end merge

end group

stop

@enduml
```
![](https://planttext.com/api/plantuml/svg/PO_12i8m38RlVOhS2tk6oK1F0s6xIsqZAcipacpgsrihmwcda2_vlv1QrB7UZcACXMxJzh1dkg9NO-q1n4MjFALJxa3Ovv9fTtQCeN-CADLhuqrtZAZ8Az9G70UyjnHm-CVvpdm9Nu4jKKD9flYXG5CPBlebLTYFgR2LmvWQKyY_FG40)
```plantuml
@startwbs

+ SAMT Project
++_ samt.conf
++ src
+++_ SharedTypes.samt
+++_ Greeter.samt
+++_ ComplexModel.samt
+++_ ComplexProvider.samt
+++_ FooConsumer.samt
++ out
+++ GreeterProvider
+++ ComplexProvider
+++ FooConsumer

@endwbs
```
