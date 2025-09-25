package com.LMS.Learning_Management_System.service;


import com.LMS.Learning_Management_System.dto.GradingDto;
import com.LMS.Learning_Management_System.dto.QuestionDto;
import com.LMS.Learning_Management_System.dto.QuizDto;
import com.LMS.Learning_Management_System.dto.StudentDto;
import com.LMS.Learning_Management_System.exception.AccessDeniedException;
import com.LMS.Learning_Management_System.exception.InsufficientQuestionsException;
import com.LMS.Learning_Management_System.exception.QuestionException;
import com.LMS.Learning_Management_System.exception.QuizException;
import com.LMS.Learning_Management_System.repository.*;
import com.LMS.Learning_Management_System.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;

@Service
public class QuizService {
    // Constants for repeated error messages
    private static final String NO_STUDENT_FOUND_MESSAGE = "No student found with this ID!";
    private static final String NO_QUIZ_FOUND_MESSAGE = "No quiz found with the given ID: ";
    private static final String NO_COURSE_FOUND_MESSAGE = "No Course found with the given ID: ";
    private static final String INVALID_TYPE_MESSAGE = "No such type";
    private static final String INSUFFICIENT_QUESTIONS_MESSAGE = "No enough Questions to create quiz!";
    private static final String NO_ACCESS_MESSAGE = "You don't have access to this feature!";
    private static final String ALREADY_SUBMITTED_MESSAGE = "You have submitted a response earlier!";
    private static final String QUESTION_EXISTS_MESSAGE = "question already exists";
    private static final String NO_QUESTION_BANK_MESSAGE = "this course doesn't have any!";
    
    private final QuizRepository quizRepository;
    private final CourseRepository courseRepository;
    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper ;
    private final StudentRepository studentRepository;
    private final GradingRepository gradingRepository;
    private final QuestionTypeRepository questionTypeRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final NotificationsService notificationsService;
    private final EnrollmentService enrollmentService;
    private final SecureRandom secureRandom = new SecureRandom();
    List<Question> quizQuestions = new ArrayList<>();
    List<Answer> quizAnswers = new ArrayList<>();
    List<Question>questionBank= new ArrayList<>();
    public QuizService(QuizRepository quizRepository, CourseRepository courseRepository, QuestionRepository questionRepository, ObjectMapper objectMapper, StudentRepository studentRepository, GradingRepository gradingRepository, QuestionTypeRepository questionTypeRepository, EnrollmentRepository enrollmentRepository, NotificationsService notificationsService, EnrollmentService enrollmentService) {
        this.quizRepository = quizRepository;
        this.courseRepository = courseRepository;
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
        this.studentRepository = studentRepository;
        this.gradingRepository = gradingRepository;
        this.questionTypeRepository = questionTypeRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.notificationsService = notificationsService;
        this.enrollmentService = enrollmentService;
    }


    public int create(Integer courseId , int typeId , HttpServletRequest request ) {
        Users loggedInInstructor = (Users) request.getSession().getAttribute("user");
        Course course= courseRepository.findById(courseId)
                .orElseThrow(() -> new EntityNotFoundException("Course not found"));
        int instructorId = course.getInstructorId().getUserAccountId();
        if (loggedInInstructor == null)
        {
            throw new IllegalArgumentException("No logged in user is found.");
        }
        else if (loggedInInstructor.getUserTypeId() == null || loggedInInstructor.getUserTypeId().getUserTypeId() != 3)
        {
            throw new IllegalArgumentException("Logged-in user is not an instructor.");
        }
        else if (instructorId != loggedInInstructor.getUserId())
        {
            throw new IllegalArgumentException("Logged-in instructor does not have access for this course.");
        }
        if(typeId>3 || typeId<1) throw new IllegalArgumentException(INVALID_TYPE_MESSAGE);
        List<Quiz> quizzes =  quizRepository.findAll();
        Quiz quiz = new Quiz();
        quiz.setCourse(course);
        quiz.setTitle("quiz"+(quizzes.size()+1));
        quiz.setQuestionCount(5);
        quiz.setRandomized(true);
        quiz.setCreationDate(new Date());

        generateQuestions(quiz,typeId, course);
        quizRepository.save(quiz);
        List<StudentDto> enrolledStudents = enrollmentService.viewEnrolledStudents(courseId,request);
        for(StudentDto student : enrolledStudents)
        {
            notificationsService.sendNotification("A new Quiz with id: "+quiz.getQuizId()+" has been uploaded " +
                    "For course: "+course.getCourseName(),student.getUserAccountId());
        }

        return quiz.getQuizId();
    }

    public String getActiveQuiz( int courseId,HttpServletRequest request)
    {
        Users loggedInUser = (Users) request.getSession().getAttribute("user");
        if (loggedInUser == null) {
            throw new IllegalArgumentException("No user is logged in.");
        }
        boolean instructor = courseRepository.findByInstructorId(loggedInUser.getUserId(),courseId);

        if(loggedInUser.getUserTypeId().getUserTypeId()==3)
        {
            if(!instructor)
                throw new IllegalArgumentException("You don't have permission to enter this quiz.");
        }
        else if(loggedInUser.getUserTypeId().getUserTypeId()==2)
        {
            boolean enrolled = enrollmentRepository.existsByStudentAndCourse(studentRepository.findById(loggedInUser.getUserId())
                            .orElseThrow(() -> new IllegalArgumentException(NO_STUDENT_FOUND_MESSAGE))
                    ,courseRepository.findById(courseId)
                            .orElseThrow(() -> new IllegalArgumentException(NO_COURSE_FOUND_MESSAGE + courseId)));
            if(!enrolled)
                throw new IllegalArgumentException("You are not enrolled this course.");
        }
        List<Quiz> quizIds = quizRepository.getQuizzesByCourseId(courseId);
        StringBuilder ids= new StringBuilder();
        for(Quiz id : quizIds)
        {
            QuizDto quizDto = new QuizDto();
            quizDto.setQuizId(id.getQuizId());
            quizDto.setCreation_date(id.getCreationDate());
           if(id.getCreationDate().getTime()+ 15 * 60 * 1000>new Date().getTime())
               ids.append("quiz with id: ").append(quizDto.getQuizId()).append(" has time left: ")
                       .append(((quizDto.getCreation_date().getTime()+(15* 60 * 1000)-new Date().getTime())/(60*1000))).append("\n");
        }
        if (ids.isEmpty()) return "No Current Quizzes\n overall Quizzes: "+quizIds.size();
        return ids.toString();
    }

    public List<QuestionDto> getQuizQuestions(int id, HttpServletRequest request) {
        Users loggedInUser = (Users) request.getSession().getAttribute("user");
        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(NO_QUIZ_FOUND_MESSAGE + id));

        if (loggedInUser == null) {
            throw new IllegalArgumentException("No user is logged in.");
        }
        boolean instructor = courseRepository.findByInstructorId(loggedInUser.getUserId(),quiz.getCourse().getCourseId());
        if(loggedInUser.getUserTypeId().getUserTypeId()==3)
        {
            if(!instructor)
                throw new IllegalArgumentException("You don't have permission to enter this quiz.");
        } else if(loggedInUser.getUserTypeId().getUserTypeId()==2)
        {
            boolean enrolled = enrollmentRepository.existsByStudentAndCourse(studentRepository.findById(loggedInUser.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException(NO_STUDENT_FOUND_MESSAGE)),quiz.getCourse());
            if(!enrolled)
                throw new IllegalArgumentException("You don't have permission to enter this course.");
            if(quiz.getCreationDate().getTime()+ 15 * 60 * 1000<new Date().getTime())
                throw new IllegalArgumentException("The quiz has been finished!");
            if (gradingRepository.boolFindGradeByQuizAndStudentID(quiz.getQuizId(),loggedInUser.getUserId()).orElse(false))
                throw new QuizException(ALREADY_SUBMITTED_MESSAGE);
        }
        quizQuestions = questionRepository.findQuestionsByQuizId(id);
        List<QuestionDto> questions =new ArrayList<>();
        for (Question q : quizQuestions) {
            QuestionDto questionDto = new QuestionDto();
            questionDto.setOptions(q.getOptions());
            questionDto.setType(q.getQuestionType().getTypeId());
            questionDto.setQuestion_text(q.getQuestionText());
            questionDto.setCorrect_answer(q.getCorrectAnswer());
            questionDto.setCourse_id(q.getCourseId().getCourseId());
            questionDto.setQuestion_id(q.getQuestionId());
            questions.add(questionDto);
        }
        return questions;
    }

    public String getType(int typeID)
    {
        if(typeID==1) return "MCQ";
        else if(typeID==2) return "TRUE_FALSE";
        else return "SHORT_ANSWER" ;
    }

    public void addQuestion(QuestionDto questionDto, HttpServletRequest request) {
        Users loggedInUser = (Users) request.getSession().getAttribute("user");
        Course course =courseRepository.findById(questionDto.getCourse_id())  // check course
                .orElseThrow(() -> new IllegalArgumentException("No course found with the given ID: " + questionDto.getCourse_id()));

        if (loggedInUser == null) {
            throw new IllegalArgumentException("No user is logged in.");
        }
        boolean instructor = courseRepository.findByInstructorId(loggedInUser.getUserId(),course.getCourseId());
        if(loggedInUser.getUserTypeId().getUserTypeId()==3)
        {
            if(!instructor)
                throw new IllegalArgumentException("You don't have permission to enter this course.");
        } else if(loggedInUser.getUserTypeId().getUserTypeId()==2)
        {
                throw new IllegalArgumentException("You don't have permission to use this feature.");
        }
        Optional<Question> optQuestion = questionRepository.findById(questionDto.getQuestion_id());
        if(optQuestion.isPresent()) throw new QuestionException(QUESTION_EXISTS_MESSAGE);
        Question question = new Question();
        question.setQuestionText(questionDto.getQuestion_text());
        // Handle QuestionType
        QuestionType questionType = questionTypeRepository.findById(questionDto.getType())
                .orElseThrow(() -> new EntityNotFoundException("No such QuestionType"+questionDto.getType()));
        question.setQuestionType(questionType);
        try {
            // Convert List<String> to JSON string
            String optionsAsString = objectMapper.writeValueAsString(questionDto.getOptions());
            question.setOptions(optionsAsString);
        } catch (Exception e) {
            throw new QuestionException("Failed to convert options to JSON", e);
        }
        question.setCourseId(course);
        question.setCorrectAnswer(questionDto.getCorrect_answer());
        questionRepository.save(question);

    }

    public void generateQuestions(Quiz quiz,int questionType, Course courseId) {

        List<Question> allQuestions = questionRepository
                .findQuestionsByCourseIdAndQuestionType(courseId.getCourseId(),questionType);  // get all questions with same type
        List<Question> emptyQuestions = questionRepository
                .findEmptyQuestionsByCourseIdAndQuestionType(courseId.getCourseId(),questionType);
        if(allQuestions.size()< 5 )
            throw new InsufficientQuestionsException(INSUFFICIENT_QUESTIONS_MESSAGE);
        if(emptyQuestions.size() < 5 )
            throw new InsufficientQuestionsException("No enough unassigned questions to create new quiz! number: "+emptyQuestions.size()+" type "+questionType);
        Set<Integer> selectedIndices = new HashSet<>();  // To track selected indices
        int count = 0;
        while (count < 5) {
            int randomNumber = secureRandom.nextInt(allQuestions.size());

            if (!selectedIndices.contains(randomNumber)) {
                selectedIndices.add(randomNumber);
                Question selectedQuestion = allQuestions.get(randomNumber);
                selectedQuestion.setQuiz(quiz);
                count++;
            }
        }
    }

    public QuizDto getQuizByID (int id, HttpServletRequest request) {
        Users loggedInUser = (Users) request.getSession().getAttribute("user");
        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(NO_QUIZ_FOUND_MESSAGE + id));

        if (loggedInUser == null) {
            throw new IllegalArgumentException("No user is logged in.");
        }
        boolean instructor = courseRepository.findByInstructorId(loggedInUser.getUserId(),quiz.getCourse().getCourseId());
        if(loggedInUser.getUserTypeId().getUserTypeId()==3)
        {
            if(!instructor)
                throw new IllegalArgumentException("You don't have permission to enter this quiz.");
        } else if(loggedInUser.getUserTypeId().getUserTypeId()==2)
        {
            boolean enrolled = enrollmentRepository.existsByStudentAndCourse(studentRepository.findById(loggedInUser.getUserId())
                            .orElseThrow(() -> new IllegalArgumentException(NO_STUDENT_FOUND_MESSAGE))
                    ,quiz.getCourse());
            if(!enrolled)
                throw new IllegalArgumentException("You don't have permission to enter this course.");
        }

        return new QuizDto(
                quiz.getQuizId(),
                quiz.getTitle(),
                quiz.getCreationDate()
                //getQuizQuestions(quiz)
        );
    }


    public void createQuestionBank(int courseId, List<QuestionDto> questions, HttpServletRequest request) {

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new EntityNotFoundException("No such Course"));
        Users loggedInUser = (Users) request.getSession().getAttribute("user");

        if (loggedInUser == null) {
            throw new IllegalArgumentException("No user is logged in.");
        }
        boolean instructor = courseRepository.findByInstructorId(loggedInUser.getUserId(),courseId);
        if(loggedInUser.getUserTypeId().getUserTypeId()==3 && !instructor) {
            throw new IllegalArgumentException("You don't have permission to enter this course.");
        } else if(loggedInUser.getUserTypeId().getUserTypeId()==2) {
            throw new AccessDeniedException(NO_ACCESS_MESSAGE);
        }

        for (QuestionDto dto : questions) {
            Question question = questionRepository.findById(dto.getQuestion_id())
                    .orElse(new Question()); // Find or create a new question

            question.setQuestionText(dto.getQuestion_text());
            try {
                String optionsAsString = objectMapper.writeValueAsString(dto.getOptions());
                question.setOptions(optionsAsString);
            } catch (Exception e) {
                throw new QuestionException("Failed to convert options to JSON", e);
            }
            question.setCorrectAnswer(dto.getCorrect_answer());
            question.setCourseId(course);

            QuestionType questionType = questionTypeRepository.findById(dto.getType())
                    .orElseThrow(() -> new EntityNotFoundException("No such QuestionType"+dto.getType()));
            question.setQuestionType(questionType);

            questionRepository.save(question);
        }
    }

    public QuizDto getQuestionBank(int courseId, HttpServletRequest request) {
        Users loggedInUser = (Users) request.getSession().getAttribute("user");

        if (loggedInUser == null) {
            throw new IllegalArgumentException("No user is logged in.");
        }
        boolean instructor = courseRepository.findByInstructorId(loggedInUser.getUserId(),courseId);
        // Verify course exists
        courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("No course found with the given ID: " + courseId));
        if(loggedInUser.getUserTypeId().getUserTypeId()==3 && !instructor) {
            throw new IllegalArgumentException("You don't have permission to enter this course.");
        } else if(loggedInUser.getUserTypeId().getUserTypeId()==2) {
            throw new IllegalArgumentException("You don't have permission to enter this feature!");
        }

        QuizDto quizDto = new QuizDto();
        questionBank = questionRepository.findQuestionsByCourseId(courseId);
        if(questionBank.isEmpty()) throw new QuestionException(NO_QUESTION_BANK_MESSAGE);
        List<QuestionDto> questionDtos = new ArrayList<>();
        for (int i = 0; i < questionBank.size(); i++) {
            QuestionDto questionDto = new QuestionDto();
            questionDto.setQuestion_id(questionBank.get(i).getQuestionId());
            questionDto.setCorrect_answer(questionBank.get(i).getCorrectAnswer());
            questionDto.setQuestion_text(questionBank.get(i).getQuestionText());
            questionDto.setType(questionBank.get(i).getQuestionType().getTypeId());
            questionDto.setCourse_id(questionBank.get(i).getCourseId().getCourseId());
            questionDto.setOptions(questionBank.get(i).getOptions());
            questionDtos.add(questionDto);
        }
        quizDto.setQuestionList(questionDtos);
        return quizDto;
    }

    // grade quiz
    public void gradeQuiz(GradingDto gradingDto, HttpServletRequest request) {
        Quiz quiz = quizRepository.findById(gradingDto.getQuiz_id())
                .orElseThrow(() -> new EntityNotFoundException("No such Quiz"));
        Users loggedInUser = (Users) request.getSession().getAttribute("user");

        if (loggedInUser == null) {
            throw new IllegalArgumentException("No user is logged in.");
        }
        boolean enrolled = enrollmentRepository.existsByStudentAndCourse(studentRepository.findById(loggedInUser.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("No Student found with this ID!")),quiz.getCourse());
        if(loggedInUser.getUserTypeId().getUserTypeId()==2)
        {
            if(!enrolled)
                throw new IllegalArgumentException("You don't have permission to enter this course.");
            if(quiz.getCreationDate().getTime()+ 15 * 60 * 1000<new Date().getTime())
                throw new IllegalArgumentException("The quiz has been finished!");
            if (gradingRepository.boolFindGradeByQuizAndStudentID(quiz.getQuizId(),loggedInUser.getUserId()).orElse(false))
                throw new QuizException(ALREADY_SUBMITTED_MESSAGE);
        }
        else throw new AccessDeniedException("You are not authorized to submit quizzes! ");
        Student student = studentRepository.findById(loggedInUser.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("No Student found with this ID!"));
          // get questions with the quiz id
        List<Question>gradedQuestions=questionRepository.findQuestionsByQuizId(gradingDto.getQuiz_id());
        List<String> answersList = gradingDto.getAnswers();
        int grade=0;
        for (int i = 0; i < gradedQuestions.size(); i++) {

            if(Objects.equals(gradedQuestions.get(i).getCorrectAnswer(), answersList.get(i)))
            {
                grade++;

            }

        }

        Grading grading = new Grading();
        grading.setGrade(grade);
        grading.setQuiz_id(quiz);
        grading.setStudent_id(student);
        gradingRepository.save(grading);
        int id  =quiz.getQuizId();
        notificationsService.sendNotification("Quiz "+id+" has been graded", loggedInUser.getUserId());

    }

    public int quizFeedback(int quizId, int studentId, HttpServletRequest request) {
        Users loggedInUser = (Users) request.getSession().getAttribute("user");
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException(NO_QUIZ_FOUND_MESSAGE + quizId));

        if (loggedInUser == null) {
            throw new IllegalArgumentException("No user is logged in.");
        }
        boolean instructor = courseRepository.findByInstructorId(loggedInUser.getUserId(),quiz.getCourse().getCourseId());

        if(loggedInUser.getUserTypeId().getUserTypeId()==3)
        {
            if(!instructor)
                throw new IllegalArgumentException("You don't have permission to enter this quiz.");
        } else if(loggedInUser.getUserTypeId().getUserTypeId()==2)
        {
            boolean enrolled = enrollmentRepository.existsByStudentAndCourse(studentRepository.findById(loggedInUser.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException(NO_STUDENT_FOUND_MESSAGE)),quiz.getCourse());
            if(!enrolled)
                throw new IllegalArgumentException("You don't have permission to enter this course.");
            if(loggedInUser.getUserId()!=studentId)
                throw new AccessDeniedException("You are not authorized to check other student's grades!");
        }
        int grade = gradingRepository.findGradeByQuizAndStudentID(quizId,studentId);
        if(grade ==-1) throw new QuizException("Quiz haven't been graded yet");
        return grade;

    }

    public List <String> quizGrades (int quizId, HttpServletRequest request)
    {
        if (quizRepository.existsById(quizId))
        {
            Quiz quiz = quizRepository.findById(quizId)
                    .orElseThrow(() -> new EntityNotFoundException("Quiz not found"));
            List <Grading> quizGrades = gradingRepository.findAllByQuizId(quiz);
            Users loggedInInstructor = (Users) request.getSession().getAttribute("user");
            int instructorId = quiz.getCourse().getInstructorId().getUserAccountId();

            if (loggedInInstructor == null)
            {
                throw new IllegalArgumentException("No logged in user is found.");
            }
            else if (loggedInInstructor.getUserTypeId() == null || loggedInInstructor.getUserTypeId().getUserTypeId() != 3)
            {
                throw new IllegalArgumentException("Logged-in user is not an instructor.");
            }
            else if (instructorId != loggedInInstructor.getUserId())
            {
                throw new IllegalArgumentException("Logged-in instructor does not have access for this quiz grades.");
            }

            List <String> grades = new ArrayList<>();
            for (Grading grading : quizGrades)
            {
                Student student = grading.getStudent_id();
                String studentGrade = "(ID)" + student.getUserAccountId() + ": (Grade)" + grading.getGrade();
                grades.add(studentGrade);
            }
            return grades;
        }
        else
        {
            throw new IllegalArgumentException("Quiz with ID " + quizId + " not found.");
        }
    }
}
