package fi.vm.sade.omatsivut.hakemus

import java.text.SimpleDateFormat
import java.util.Date

import fi.vm.sade.haku.oppija.lomake.domain.elements.custom.gradegrid.{GradeGrid, GradeGridAddLang, GradeGridOptionQuestion, GradeGridTitle}
import fi.vm.sade.haku.oppija.lomake.domain.elements.custom.{PostalCode, PreferenceRow, PreferenceTable, SocialSecurityNumber}
import fi.vm.sade.haku.oppija.lomake.domain.elements.questions.{CheckBox, DateQuestion, OptionQuestion, TextArea, TextQuestion}
import fi.vm.sade.haku.oppija.lomake.domain.elements.{HiddenValue, Link, Notification, Phase, Text, Theme, TitledGroup}
import fi.vm.sade.haku.oppija.lomake.domain.rules.{AddElementRule, RelatedQuestionRule}
import fi.vm.sade.omatsivut.config.SpringContextComponent
import fi.vm.sade.omatsivut.domain.{Address, Attachment, Language}
import fi.vm.sade.omatsivut.localization.Translations
import fi.vm.sade.omatsivut.lomake.{ElementWrapper, OptionWrapper}
import fi.vm.sade.omatsivut.servlet.ServerContaxtPath
import fi.vm.sade.utils.slf4j.Logging

import scalatags.Text.TypedTag

trait HakemusPreviewGeneratorComponent {
  this: SpringContextComponent with HakemusRepositoryComponent with HakemusConverterComponent =>

  def newHakemusPreviewGenerator(language: Language.Language): HakemusPreviewGenerator

  class HakemusPreviewGenerator(language: Language.Language) extends Logging {
    import scala.collection.JavaConversions._
    import scalatags.Text.all._
    private val applicationSystemService = springContext.applicationSystemService
    private implicit val lang = language

    def generatePreview(serverPath: ServerContaxtPath, personOid: String, applicationOid: String): Option[String] = {
      applicationRepository.findStoredApplicationByPersonAndOid(personOid, applicationOid).map { application =>
        applicationPreview(serverPath, application)
      }
    }

    private def applicationPreview(serverPath: ServerContaxtPath, application: ImmutableLegacyApplicationWrapper) = {
      val applicationSystem = applicationSystemService.getApplicationSystem(application.hakuOid)
      val form = ElementWrapper.wrapFiltered(applicationSystem.getForm, application.flatAnswers)
      val addInfos = for (addInfo <- applicationSystem.getAdditionalInformationElements()) yield {
        ElementWrapper.wrapFiltered(addInfo, application.flatAnswers)
      }

      def questionsPreview(element: ElementWrapper, showEmptyValues: Boolean): List[TypedTag[String]] = {
        element.element match {
          case _: GradeGrid => List(gradeGridPreview(element))
          case _: PreferenceTable => List(preferenceTablePreview(element))
          case _: TextArea => textQuestionPreview(element)
          case _: SocialSecurityNumber => textQuestionPreview(element)
          case _: TextQuestion => textQuestionPreview(element, showEmptyValues)
          case _: PostalCode => postalCodePreview(element)
          case _: OptionQuestion => optionQuestionPreview(element)
          case _: CheckBox => checkBoxPreview(element)
          case _: Theme => themePreview(element)
          case _: Phase => childrenPreview(element)
          case _: RelatedQuestionRule => childrenPreview(element)
          case _: Text => List(textPreview(element))
          case _: Notification => List(textPreview(element))
          case link: Link => List(linkPreview(element, link))
          case _: TitledGroup => List(titledGroupPreview(element))
          case _: DateQuestion => List(textPreview(element))
          case _: AddElementRule => childrenPreview(element, false)
          case _: HiddenValue => Nil // info about attachments are added separately in the end of the document
          case _ =>
            logger.warn("Ignoring element " + element.element.getType + ": " + element.id)
            Nil
        }
      }

      def additionalInformationElementsPreview(): List[TypedTag[String]] = {
        (for(addInfo <- addInfos) yield additionalInformationElementPreview(addInfo)
        ).flatten.toList
      }

      def additionalInformationElementPreview(element: ElementWrapper): List[TypedTag[String]] = {
        if(element.children.isEmpty) {
          Nil
        }
        else {
          List(div(`class` := "theme")(
              hr(),
              questionsPreview(element, true)
          ))
        }
      }

      def attachmentsAnchor(): List[TypedTag[String]] = {
        List(a(name := "liitteet"))
      }

      def attachmentsInfoPreview(): List[TypedTag[String]] = {
        val aoInfos = application.attachments.map(AttachmentConverter.convertToAttachment(_))

        if (aoInfos.isEmpty) {
          Nil
        }
        else {
            List(div(`class` := "theme")(
              h2(Translations.getTranslation("applicationPreview", "attachments")),
              table(`class` := "striped") (
                tr(
                  th(Translations.getTranslation("applicationPreview", "attachment")),
                  th(Translations.getTranslation("applicationPreview", "attachmentAddress")),
                  th(Translations.getTranslation("applicationPreview", "attachmentDeadline"))
                ),
                for (info <- aoInfos) yield attachmentInfoPreview(info))
              )
            )
        }
      }

      def attachmentInfoPreview(info: Attachment): List[TypedTag[String]] = {
        List(tr(
          td(
            elemIfNotEmptyString(p(_), info.name),
            elemIfNotEmptyString(text => p(text), info.heading),
            elemIfNotEmptyString(text => div(raw(text)), info.description)
          ),
          td(elemIfNotEmptyString(div(_), info.recipientName ),
             attachmentAddressInfoPreview(info.address)),
          td(elemIfNotEmpty[Long](div(_), info.deadline, formatDeadlinePreview))

        ))
      }

      def attachmentAddressInfoPreview(address: Option[Address]): List[TypedTag[String]] = {
        if(address.isDefined) {
          List(
            elemIfNotEmptyString(div(_), address.get.streetAddress),
            elemIfNotEmptyString(div(_), address.get.streetAddress2),
            elemIfNotEmptyString(div(_), address.get.postalCode),
            elemIfNotEmptyString(div(_), address.get.postOffice )
          ).flatten
        }
        else {
          Nil
        }
      }

      def formatDeadlinePreview(date: Long): String = {
        formatDate(Some(new Date(date)))
      }

      def elemIfNotEmptyString(elem: String => TypedTag[String], value: Option[String]): Option[TypedTag[String]] = {
        elemIfNotEmpty[String](elem, value, _.toString())
      }

      def elemIfNotEmpty[T](elem: String => TypedTag[String], value: Option[T], format: T => String): Option[TypedTag[String]] = {
        if(value.isDefined && format(value.get).trim().length() > 0 ) {
          Some(elem(format(value.get).trim()))
        }
        else {
          None
        }
      }

      def themePreview(element: ElementWrapper): List[TypedTag[String]] = {
        if(element.children.isEmpty) {
          Nil
        }
        else {
          List(div(`class` := "theme")(h2(raw(element.title)), childrenPreview(element)))
        }
      }

      def childrenPreview(element: ElementWrapper, showEmptyValues: Boolean = true) = {
        element.children.flatMap(questionsPreview(_, showEmptyValues))
      }

      def questionPreview(question: String, answer: String, showEmptyValues: Boolean = true) = {
        if(answer.trim().isEmpty() && !showEmptyValues) {
          Nil
        }
        else {
          div(`class` := "question")(
            previewLabel(question),
            span(`class` := "answer")(answer)
          ) :: Nil
        }
      }

      def previewLabel(question: String) = {
        if(question.isEmpty) {
          label(raw("&nbsp;"))
        }
        else {
          label(raw(question))
        }
      }

      def textQuestionPreview(element: ElementWrapper, showEmptyValues: Boolean = true) = {
        questionPreview(element.title, application.flatAnswers.get(element.id).getOrElse("").asInstanceOf[String], showEmptyValues)
      }

      def postalCodePreview(element: ElementWrapper) = {
        val answer = selectedOption(element.options, element.id)
          .map { option => option.value + " " + option.title }
          .getOrElse("")
        questionPreview("", answer) ::: childrenPreview(element)
      }

      def optionQuestionPreview(element: ElementWrapper) = {
        val answer = answerFromOptions(element.options, element.id)
        questionPreview(element.title, answer) ::: childrenPreview(element)
      }

      def selectedOption(options: List[OptionWrapper], key: String) = {
        options
          .find { option => Some(option.value) == application.flatAnswers.get(key)}
      }

      def answerFromOptions(options: List[OptionWrapper], key: String) = {
        selectedOption(options, key)
          .map { option => option.title }
          .getOrElse("")
      }

      def checkBoxPreview(element: ElementWrapper) = {
        val answer: Option[String] = application.flatAnswers.get(element.id)
        val checkBoxValue = element.element.asInstanceOf[CheckBox].getValue
        val checked = (answer == Some(checkBoxValue))
        val translatedAnswer = checked match {
          case true => Translations.getTranslation("message", "yes")
          case false => Translations.getTranslation("message", "no")
        }
        questionPreview(element.title, translatedAnswer) ::: childrenPreview(element)
      }

      def titledGroupPreview(element: ElementWrapper) = {
        div(`class` := "group")(
          h3(raw(element.title)) :: childrenPreview(element)
        )
      }

      def preferenceTablePreview(tableElement: ElementWrapper) = {
        ul(`class` := "preference-table")(
          tableElement.children.zipWithIndex.flatMap { case (childElement, index) =>
            childElement.element match {
              case row: PreferenceRow =>
                application.flatAnswers(row.getEducationOidInputId) match {
                  case null => Nil
                  case "" => Nil
                  case _ => List(li(`class` := "preference-row")(
                    List(
                      span(`class` := "index")(index+1),
                      span(`class` := "learning-institution")(
                        label(ElementWrapper.t(row.getLearningInstitutionLabel)),
                        span(application.flatAnswers(row.getLearningInstitutionInputId))
                      ),
                      span(`class` := "education")(
                        label(ElementWrapper.t(row.getEducationLabel)),
                        span(application.flatAnswers(row.getEducationInputId))
                      )
                    ) ++ preferenceQuestionsPreview(childElement)
                  ))
                }
              case _ =>
                logger.warn("Ignoring preference table element " + childElement.element.getType + ": " + childElement.id)
                Nil
            }
          }
        )
      }

      def preferenceQuestionsPreview(element: ElementWrapper) = {
        childrenPreview(element).toList match {
          case Nil =>
            Nil
          case children =>
            List(div(`class` := "questions")(h3(Translations.getTranslation("applicationPreview", "preferences_questions")), children))
        }
      }

      def attrs(el: ElementWrapper): List[Modifier] = {
        el.element.getAttributes.toMap.map { case (key, value) =>
          key.attr := value
        }.toList
      }

      def gradeGridPreview(gridElement: ElementWrapper) = {
        table(`class` := "gradegrid")(
          thead(
            td("colspan".attr := 2)(Translations.getTranslation("applicationPreview", "subject")) :: (if (gridElement.element.asInstanceOf[GradeGrid].isExtraColumn) {
              List(td(Translations.getTranslation("applicationPreview", "mutual_subject")), td(Translations.getTranslation("applicationPreview", "optional_subject")), td(Translations.getTranslation("applicationPreview", "second_optional_subject")))
            } else {
              List(td(Translations.getTranslation("applicationPreview", "grade")))
            })
          ),
          tbody(gridElement.children.map { row =>
            tr(row.children.map { column =>
              td(attrs(column))( // <- practically there's a colspan attribute that needs to be set
                column.children.map { dataElement =>
                  gradeGridElementPreview(answerFromOptions _, dataElement)
                }
              )
            }
            )
          })
        )
      }

      def gradeGridElementPreview(answerFromOptions: (List[OptionWrapper], String) => String, dataElement: ElementWrapper): List[String] = {
        dataElement.element match {
          case _: GradeGridTitle => List(dataElement.title)
          case _: GradeGridOptionQuestion =>
            val gradeValue = answerFromOptions(dataElement.options, dataElement.id)
            val kymppiValue = answerFromOptions(dataElement.options, dataElement.id + "_10")
            List(kymppiValue match {
              case "" => gradeValue
              case value => value + "(" + gradeValue + ")"
            })
          case _: GradeGridAddLang => Nil
          case _ =>
            logger.warn("Ignoring grade grid element " + dataElement.element.getType + ": " + dataElement.id)
            Nil
        }
      }

      def textPreview(element: ElementWrapper) = {
        div(`class` := "text")(element.title)
      }

      def linkPreview(element: ElementWrapper, link: Link) = {
        a(`href` := ElementWrapper.t(link.getUrl()), `target` := "_blank")(element.title)
      }

      def formatOid(oid: String): String = {
        val split = oid.split("\\.").toList
        split match {
          case Nil => ""
          case parts => parts.last
        }
      }

      def formatDate(date: Option[Date]) = date match {
        case Some(date) => new SimpleDateFormat("dd.MM.yyyy HH:mm").format(date)
        case None => ""
      }

      """<!DOCTYPE html>""" +
      html(
        head(link(href := "/omatsivut/css/preview.css", rel:= "stylesheet", `type` := "text/css")),
        body(
          header(
            h1(ElementWrapper.t(applicationSystem.getName)),
            h2(application.flatAnswers.get("Etunimet").getOrElse("") + " " + application.flatAnswers.get("Sukunimi").getOrElse("")),
            div(`class` := "detail application-received")(label(Translations.getTranslation("applicationPreview", "received")), span(formatDate(application.received))),
            div(`class` := "detail application-id")(label(Translations.getTranslation("applicationPreview", "applicationId")), span(formatOid(application.oid)))
          )
          ::
          form.getElementsOfType[Phase].flatMap(questionsPreview(_, true))
          :::
          additionalInformationElementsPreview()
          :::
          attachmentsAnchor()
          :::
          attachmentsInfoPreview()
          ,
          script(`type` := "text/javascript", src := "/omatsivut/piwik/load")
        )
      )
    }
  }

}

