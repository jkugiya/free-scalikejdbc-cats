val scalacOpts =
  "-deprecation" ::
      "-unchecked" ::
      "-language:existentials" ::
      "-language:higherKinds" ::
      "-language:implicitConversions" ::
      Nil

val scalaVersion_        = "2.13.0"
val scalikejdbcVersion   = "3.3.+"
val catsVersion          = "2.0.0-RC1"
val scalatestVersion     = "3.0.8"
val scalacheckVersion    = "1.14.0"
val h2Version            = "1.4.+"
val kindProjectorVersion = "0.10.3"

lazy val core = (project in file("core")).settings(
  name := """free-scalikejdbc-cats""",
  version := "1.0",
  scalaVersion := scalaVersion_,
  resolvers += "bintray/non".at("http://dl.bintray.com/non/maven"),
  libraryDependencies ++=
      ("org.scalikejdbc"     %% "scalikejdbc"                      % scalikejdbcVersion) ::
          ("org.typelevel"   %% "cats-core"                        % catsVersion) ::
          ("org.typelevel"   %% "cats-free"                        % catsVersion) ::
          ("org.typelevel"   %% "cats-laws"                        % catsVersion) ::
          ("com.h2database"  % "h2"                                % h2Version % "test") ::
          ("org.scalikejdbc" %% "scalikejdbc-test"                 % scalikejdbcVersion % "test") ::
          ("org.scalikejdbc" %% "scalikejdbc-config"               % scalikejdbcVersion % "test") ::
          ("org.scalikejdbc" %% "scalikejdbc-syntax-support-macro" % scalikejdbcVersion % "test") ::
          ("org.scalatest"   %% "scalatest"                        % scalatestVersion % "test") ::
          ("org.scalacheck"  %% "scalacheck"                       % scalacheckVersion % "test") ::
          Nil,
  scalacOptions ++= scalacOpts,
  parallelExecution in Test := false,
  addCompilerPlugin("org.typelevel" %% "kind-projector" % kindProjectorVersion))

lazy val sample = (project in file("sample"))
  .settings(
    name := """free-scalikejdbc-sample""",
    version := "1.0",
    scalaVersion := scalaVersion_,
    resolvers ++= ("bintray/non".at("http://dl.bintray.com/non/maven")) :: Resolver.sonatypeRepo("releases") :: Nil,
    libraryDependencies ++=
        ("org.scalikejdbc"     %% "scalikejdbc-config"               % scalikejdbcVersion) ::
            ("org.scalikejdbc" %% "scalikejdbc-syntax-support-macro" % scalikejdbcVersion) ::
            ("com.h2database"  % "h2"                                % h2Version) ::
            ("org.scalatest"   %% "scalatest"                        % scalatestVersion % "test") ::
            ("org.scalacheck"  %% "scalacheck"                       % scalacheckVersion % "test") ::
            Nil,
    scalacOptions ++= scalacOpts,
    addCompilerPlugin("org.typelevel" %% "kind-projector" % kindProjectorVersion))
  .dependsOn(core)

lazy val root = (project in file(".")).aggregate(core, sample)
